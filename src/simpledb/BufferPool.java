package simpledb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * BufferPool manages the reading and writing of pages into memory from
 * disk. Access methods call into it to retrieve pages, and it fetches
 * pages from the appropriate location.
 * <p>
 * The BufferPool is also responsible for locking;  when a transaction fetches
 * a page, BufferPool checks that the transaction has the appropriate
 * locks to read/write the page.
 *
 * @Threadsafe, all fields are final
 */
public class BufferPool {
    /**
     * Bytes per page, including header.
     */
    private static final int DEFAULT_PAGE_SIZE = 4096;

    private static int pageSize = DEFAULT_PAGE_SIZE;

    /**
     * Default number of pages passed to the constructor. This is used by
     * other classes. BufferPool should use the numPages argument to the
     * constructor instead.
     */
    public static final int DEFAULT_PAGES = 50;

    private final int numPages;
    private volatile ConcurrentHashMap<PageId, Page> bufferPool;
    private volatile LinkedList<PageId> pageIdCache = new LinkedList<>();
    private volatile ConcurrentHashMap<PageId, ReentrantReadWriteLock> lock;
    private volatile ConcurrentHashMap<TransactionId, TransactionsLock> transaction;

    private static class TransactionsLock {
        TransactionId tid;
        ConcurrentHashMap<PageId, ReentrantReadWriteLock> lock;
        ConcurrentHashMap<ReentrantReadWriteLock, Permissions> permission;

        public TransactionsLock(TransactionId tid) {
            this.tid = tid;
            lock = new ConcurrentHashMap<>();
            permission = new ConcurrentHashMap<>();
        }
    }

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        this.numPages = numPages;
        bufferPool = new ConcurrentHashMap<>(numPages);
        lock = new ConcurrentHashMap<>(numPages);
        transaction = new ConcurrentHashMap<>();
    }

    public static int getPageSize() {
        return pageSize;
    }

    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void setPageSize(int pageSize) {
        BufferPool.pageSize = pageSize;
    }

    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void resetPageSize() {
        BufferPool.pageSize = DEFAULT_PAGE_SIZE;
    }

    /**
     * Retrieve the specified page with the associated permissions.
     * Will acquire a lock and may block if that lock is held by another
     * transaction.
     * <p>
     * The retrieved page should be looked up in the buffer pool.  If it
     * is present, it should be returned.  If it is not present, it should
     * be added to the buffer pool and returned.  If there is insufficient
     * space in the buffer pool, a page should be evicted and the new page
     * should be added in its place.
     *
     * @param tid  the ID of the transaction requesting the page
     * @param pid  the ID of the requested page
     * @param perm the requested permissions on the page
     */
    public Page getPage(TransactionId tid, PageId pid, Permissions perm)
            throws TransactionAbortedException, DbException {
        //如果事务正在access这个page则根据情况修改perm并返回page
        if (transaction.containsKey(tid) && transaction.get(tid).lock.containsKey(pid)) {
            TransactionsLock transactionsLock = transaction.get(tid);
            ReentrantReadWriteLock reentrantReadWriteLock = transactionsLock.lock.get(pid);
            Permissions permissions = transactionsLock.permission.get(reentrantReadWriteLock);
            if (permissions == Permissions.READ_ONLY && perm == Permissions.READ_WRITE) {
                ReentrantReadWriteLock newReentrantReadWriteLock = new ReentrantReadWriteLock();
                newReentrantReadWriteLock.writeLock().lock();//可能有其他读者
                lock.put(pid, newReentrantReadWriteLock);
//                reentrantReadWriteLock.readLock().unlock();//违反了两段锁协议,不是当前线程的
                //原来的读锁被抛弃了，还没有解锁
                transactionsLock.lock.put(pid, newReentrantReadWriteLock);
                transactionsLock.permission.remove(reentrantReadWriteLock);
                transactionsLock.permission.put(newReentrantReadWriteLock, Permissions.READ_WRITE);
            }
            if (bufferPool.containsKey(pid)) {
                return bufferPool.get(pid);
            } else {
                if (bufferPool.size() < numPages) {
                    Page page = Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid);
                    bufferPool.put(pid, page);
                    pageIdCache.offer(pid);
                    return page;
                } else {
                    //evict一个clean页,且不在某个事务执行中
                    for (int i = 0; i < pageIdCache.size(); i++) {
                        if (bufferPool.get(pageIdCache.get(i)).isDirty() == null) {
                            PageId poll = pageIdCache.get(i);
                            discardPage(poll);
                            return getPage(tid, pid, perm);
                        }
                    }
                    throw new DbException("All bufferPool pages are dirty!");
                }
            }
        }
        //tid还没接触到此pid，根据读写锁判断能否访问，重入锁计数
        {
            //说明这个页要有🔒了
            synchronized (BufferPool.class) {
                if (!lock.containsKey(pid)) {
                    lock.put(pid, new ReentrantReadWriteLock());
                }
            }
            //按需🔒住
            if (perm == Permissions.READ_ONLY) {
                lock.get(pid).readLock().lock();
            } else {
                lock.get(pid).writeLock().lock();
            }
            //事务关联->锁
            TransactionsLock transactionsLock = null;
            synchronized (BufferPool.class) {
                if (!transaction.containsKey(tid)) {
                    transactionsLock = new TransactionsLock(tid);
                    transaction.put(tid, transactionsLock);
                } else {
                    transactionsLock = transaction.get(tid);
                }
            }
            assert (transactionsLock != null);
            transactionsLock.lock.put(pid, lock.get(pid));
            transactionsLock.permission.put(lock.get(pid), perm);
        }
        //去获得页面
        if (bufferPool.containsKey(pid)) {
            return bufferPool.get(pid);
        } else {
            if (bufferPool.size() < numPages) {
                Page page = Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid);
                bufferPool.put(pid, page);
                pageIdCache.offer(pid);
                return page;
            } else {
                //evict一个clean页,且不在某个事务执行中
                for (int i = 0; i < pageIdCache.size(); i++) {
                    if (bufferPool.get(pageIdCache.get(i)).isDirty() == null) {
                        PageId poll = pageIdCache.get(i);
                        discardPage(poll);
                        return getPage(tid, pid, perm);
                    }
                }
                throw new DbException("All bufferPool pages are dirty!");
            }
        }
    }

    /**
     * Releases the lock on a page.
     * Calling this is very risky, and may result in wrong behavior. Think hard
     * about who needs to call this and why, and why they can run the risk of
     * calling it.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param pid the ID of the page to unlock
     */
    public void releasePage(TransactionId tid, PageId pid) {
        TransactionsLock transactionsLock = transaction.get(tid);
        ReentrantReadWriteLock reentrantReadWriteLock = transactionsLock.lock.get(pid);
        if (transactionsLock.permission.get(reentrantReadWriteLock) == Permissions.READ_ONLY) {
            transactionsLock.lock.get(pid).readLock().unlock();
        } else {
            transactionsLock.lock.get(pid).writeLock().unlock();
        }
        transactionsLock.permission.remove(reentrantReadWriteLock);
        transactionsLock.lock.remove(pid);
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public void transactionComplete(TransactionId tid) throws IOException {
        transactionComplete(tid, true);
    }

    /**
     * Return true if the specified transaction has a lock on the specified page
     */
    public boolean holdsLock(TransactionId tid, PageId p) {
        return transaction.get(tid).lock.containsKey(p);
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid    the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit)
            throws IOException {
        if (transaction.containsKey(tid)) {
            if (commit == true) {
                flushPages(tid);
                TransactionsLock transactionsLock = transaction.get(tid);
                Iterator<ReentrantReadWriteLock> iterator = transactionsLock.lock.values().iterator();
                while (iterator.hasNext()) {
                    ReentrantReadWriteLock next = iterator.next();
                    if (transactionsLock.permission.get(next) == Permissions.READ_ONLY) {
                        if (next.getReadHoldCount() != 0) {
                            next.readLock().unlock();
                        }

                    } else {
                        if (next.getWriteHoldCount() != 0) {
                            next.writeLock().unlock();
                        }
                    }
                }
                transaction.remove(tid);
            }
            if (commit == false) {
                TransactionsLock transactionsLock = transaction.get(tid);
                Iterator<PageId> iterator = transactionsLock.lock.keySet().iterator();
                while (iterator.hasNext()) {
                    PageId next = iterator.next();
                    if (bufferPool.containsKey(next)) {
                        bufferPool.put(next, bufferPool.get(next).getBeforeImage());
                    }
                }
                transactionComplete(tid, true);
            }
        }

    }

    /**
     * Add a tuple to the specified table on behalf of transaction tid.  Will
     * acquire a write lock on the page the tuple is added to and any other
     * pages that are updated (Lock acquisition is not needed for lab2).
     * May block if the lock(s) cannot be acquired.
     * <p>
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid     the transaction adding the tuple
     * @param tableId the table to add the tuple to
     * @param t       the tuple to add
     */
    public void insertTuple(TransactionId tid, int tableId, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        ArrayList<Page> arrayList = Database.getCatalog().getDatabaseFile(tableId).insertTuple(tid, t);
        for (Page p : arrayList) {
            p.markDirty(true, tid);
        }
    }

    /**
     * Remove the specified tuple from the buffer pool.
     * Will acquire a write lock on the page the tuple is removed from and any
     * other pages that are updated. May block if the lock(s) cannot be acquired.
     * <p>
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid the transaction deleting the tuple.
     * @param t   the tuple to delete
     */
    public void deleteTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        ArrayList<Page> dirtyPages = Database.getCatalog().getDatabaseFile(t.getRecordId().getPageId().getTableId()).deleteTuple(tid, t);
        for (Page p : dirtyPages) {
            p.markDirty(true, tid);
        }
    }


    /**
     * Flush all dirty pages to disk.
     * NB: Be careful using this routine -- it writes dirty data to disk so will
     * break simpledb if running in NO STEAL mode.
     */
    public synchronized void flushAllPages() throws IOException {
        Iterator<PageId> iterator = bufferPool.keySet().iterator();
        while (iterator.hasNext()) {
            PageId next = iterator.next();
            if ((bufferPool.get(next)).isDirty() != null) {
                flushPage(next);
            }
        }
    }

    /**
     * Remove the specific page id from the buffer pool.
     * Needed by the recovery manager to ensure that the
     * buffer pool doesn't keep a rolled back page in its
     * cache.
     * <p>
     * Also used by B+ tree files to ensure that deleted pages
     * are removed from the cache so they can be reused safely
     */
    public synchronized void discardPage(PageId pid) {
        bufferPool.remove(pid);
        pageIdCache.remove(pid);
    }

    /**
     * Flushes a certain page to disk
     *
     * @param pid an ID indicating the page to flush
     */
    private synchronized void flushPage(PageId pid) throws IOException {
        Page page = bufferPool.get(pid);
        Database.getCatalog().getDatabaseFile(pid.getTableId()).writePage(page);
    }

    /**
     * Write all pages of the specified transaction to disk.
     */
    public synchronized void flushPages(TransactionId tid) throws IOException {
        if (transaction.containsKey(tid)) {
            TransactionsLock transactionsLock = transaction.get(tid);
            Iterator<PageId> iterator = transactionsLock.lock.keySet().iterator();
            while (iterator.hasNext()) {
                PageId next = iterator.next();
                if (bufferPool.containsKey(next) && bufferPool.get(next).isDirty() != null) {
                    flushPage(next);
                }
            }
        }
        //什么时候markDirty clean？
    }

    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     */
    private synchronized void evictPage() throws DbException {
        //丢弃一个page？
    }
}
