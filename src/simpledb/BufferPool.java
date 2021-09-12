package simpledb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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
    private ConcurrentHashMap<PageId, Page> bufferPool;
    private LinkedList<PageId> pageIdCache;
    private ConcurrentHashMap<PageId, PageLock> pageId2pageLock;
    private ConcurrentHashMap<TransactionId, TransactionLock> transactionId2hasLock;

    private static class PageLock {
        public final Semaphore fifoRw = new Semaphore(1);
        public final Semaphore rw = new Semaphore(1);
        public final Semaphore mutex = new Semaphore(1);
        public volatile int readCount = 0;

    }

    private static class TransactionLock {
        TransactionId tid;
        LinkedList<PageId> pageIdLinkedList = new LinkedList<>();
        ConcurrentHashMap<PageLock, Permissions> hasLock = new ConcurrentHashMap<>();

        public TransactionLock(TransactionId tid) {
            this.tid = tid;
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
        pageIdCache = new LinkedList<>();
        pageId2pageLock = new ConcurrentHashMap<>();
        transactionId2hasLock = new ConcurrentHashMap<>();
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
        //页->页锁
        pageId2pageLock.putIfAbsent(pid, new PageLock());
        PageLock pageLock = pageId2pageLock.get(pid);
        //事务->事务锁集合
        transactionId2hasLock.putIfAbsent(tid, new TransactionLock(tid));
        TransactionLock transactionLock = transactionId2hasLock.get(tid);
        //按需🔒住,先不管中断异常
        if (perm == Permissions.READ_ONLY) {
            if (!transactionLock.hasLock.containsKey(pageLock)) {
                lockInShareMode(transactionLock, pid, pageLock);
            }
        } else {
            lockForUpdate(tid, transactionLock, pid, pageLock);
        }
        //获得页面
        if (bufferPool.containsKey(pid)) {
            return bufferPool.get(pid);
        } else {
            synchronized (Database.getBufferPool()) {
                if (bufferPool.size() < numPages) {
                    Page page = Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid);
                    bufferPool.put(pid, page);
                    pageIdCache.offer(pid);
                    return page;
                } else {
                    //discard一个clean页,且不在某个事务执行中(好像可以释放事务执行中的clean页)
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
    }

    private void lockInShareMode(TransactionLock transactionLock, PageId pid, PageLock pageLock) throws TransactionAbortedException {
        try {
            pageLock.fifoRw.acquire();
            pageLock.mutex.acquire();
            if (pageLock.readCount == 0) {
                if (!pageLock.rw.tryAcquire(120, TimeUnit.MILLISECONDS)) {
                    throw new TransactionAbortedException();
                }
            }
            transactionLock.hasLock.put(pageLock, Permissions.READ_ONLY);
            transactionLock.pageIdLinkedList.add(pid);
            pageLock.readCount++;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            pageLock.mutex.release();
            pageLock.fifoRw.release();
        }
    }

    private void lockForUpdate(TransactionId tid, TransactionLock transactionLock, PageId pid, PageLock pageLock) throws TransactionAbortedException {
        try {
            pageLock.fifoRw.acquire();
            if (transactionLock.hasLock.containsKey(pageLock)) {
                if (transactionLock.hasLock.get(pageLock) != Permissions.READ_WRITE) {
                    //读锁升级写锁
                    pageLock.mutex.acquire();
                    if (--pageLock.readCount == 0) {
                        pageLock.rw.release();
                    }
                    pageLock.mutex.release();
                    if (!pageLock.rw.tryAcquire(120, TimeUnit.MILLISECONDS)) {
                        pageLock.mutex.acquire();
                        pageLock.readCount++;
                        pageLock.mutex.release();
                        //1
                        try {
                            transactionComplete(tid, false);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        throw new TransactionAbortedException();
                    }
                    transactionLock.hasLock.put(pageLock, Permissions.READ_WRITE);
                }
            }
            if (!transactionLock.hasLock.containsKey(pageLock)) {
                if (!pageLock.rw.tryAcquire(120, TimeUnit.MILLISECONDS)) {
                    throw new TransactionAbortedException();
                    //2
                }
                transactionLock.hasLock.put(pageLock, Permissions.READ_WRITE);
            }
            if (!transactionLock.pageIdLinkedList.contains(pid)) {
                transactionLock.pageIdLinkedList.add(pid);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //放这里是因为1、2处都要做这个操作，为了统一
            pageLock.fifoRw.release();
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
        TransactionLock transactionLock = transactionId2hasLock.get(tid);
        PageLock pageLock = pageId2pageLock.get(pid);
        Permissions permissions = transactionLock.hasLock.get(pageLock);
        if (permissions == Permissions.READ_ONLY) {
            try {
                pageLock.mutex.acquire();
                if (--pageLock.readCount == 0) {
                    pageLock.rw.release();
                }
                pageLock.mutex.release();
                transactionLock.hasLock.remove(pageLock);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            transactionLock.hasLock.remove(pageLock);
            pageLock.rw.release();
        }
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
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid    the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit)
            throws IOException {
        if (transactionId2hasLock.containsKey(tid)) {
            TransactionLock transactionLock = transactionId2hasLock.get(tid);
            if (commit) {
                for (Map.Entry<PageLock, Permissions> next : transactionLock.hasLock.entrySet()) {
                    PageLock pageLock = next.getKey();
                    if (next.getValue() == Permissions.READ_ONLY) {
                        try {
                            pageLock.mutex.acquire();
                            if (--pageLock.readCount == 0) {
                                pageLock.rw.release();
                            }
                            pageLock.mutex.release();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (next.getValue() == Permissions.READ_WRITE) {
                        for (PageId nextPageId : transactionLock.pageIdLinkedList) {
                            flushPage(nextPageId);
                        }
                        pageLock.rw.release();
                    }
                }
                transactionId2hasLock.remove(tid);
            } else {
                for (PageId next : transactionLock.pageIdLinkedList) {
                    Page page = bufferPool.get(next);
                    if (page != null) {
                        bufferPool.put(next, bufferPool.get(next).getBeforeImage());
                    }
                    //这样写会有线程安全问题
//                    if (bufferPool.containsKey(next)) {
//                        bufferPool.put(next, bufferPool.get(next).getBeforeImage());
//                    }
                }
            }
            transactionComplete(tid, true);
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
     * Remove the specific page id from the buffer pool.
     * Needed by the recovery manager to ensure that the
     * buffer pool doesn't keep a rolled back page in its
     * cache.
     * <p>
     * Also used by B+ tree files to ensure that deleted pages
     * are removed from the cache so they can be reused safely
     */
    public void discardPage(PageId pid) {
        synchronized (Database.getBufferPool()) {
            bufferPool.remove(pid);
            pageIdCache.remove(pid);
        }
    }

    /**
     * Flushes a certain page to disk
     *
     * @param pid an ID indicating the page to flush
     */
    private synchronized void flushPage(PageId pid) throws IOException {
        Page page = bufferPool.get(pid);
        //page != null 判断很关键
        if (page != null && page.isDirty() != null) {
            Database.getCatalog().getDatabaseFile(pid.getTableId()).writePage(page);
            //page.markDirty(false, null);
            //因为要discard了，就没必要markClean了
            discardPage(pid);
        }
    }

    /**
     * Flush all dirty pages to disk.
     * NB: Be careful using this routine -- it writes dirty data to disk so will
     * break simpledb if running in NO STEAL mode.
     */
    //Test for
    public synchronized void flushAllPages() throws IOException {
        for (PageId next : bufferPool.keySet())
            if ((bufferPool.get(next)).isDirty() != null) {
                flushPage(next);
            }
    }


    /**
     * Write all pages of the specified transaction to disk.
     */
    //Test for
    public synchronized void flushPages(TransactionId tid) throws IOException {
        if (transactionId2hasLock.containsKey(tid)) {
            TransactionLock transactionsLock = transactionId2hasLock.get(tid);
            for (PageId next : transactionsLock.pageIdLinkedList)
                if (bufferPool.containsKey(next) && bufferPool.get(next).isDirty() != null) {
                    flushPage(next);
                }
        }
    }
}
