package simpledb;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 *
 * @author Sam Madden
 * @see simpledb.HeapPage#HeapPage
 */
public class HeapFile implements DbFile {

    private File f;
    private TupleDesc td;

    /**
     * Constructs a heap file backed by the specified file.
     *
     * @param f the file that stores the on-disk backing store for this heap
     *          file.
     */
    public HeapFile(File f, TupleDesc td) {
        this.f = f;
        this.td = td;
    }

    /**
     * Returns the File backing this HeapFile on disk.
     *
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        return f;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere to ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     *
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        return f.getAbsoluteFile().hashCode();//mapping?
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     *
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        return td;
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid) {
        if (pid.getPageNumber() >= numPages()) {
            throw new IllegalStateException();
        }
        int pageSize = BufferPool.getPageSize();
        byte[] data = new byte[pageSize];
        RandomAccessFile randomAccessFile = null;
        try {
            randomAccessFile = new RandomAccessFile(f, "r");
            int skip = pid.getPageNumber() * pageSize;
            randomAccessFile.seek(skip);
            randomAccessFile.read(data, 0, pageSize);
            return new HeapPage((HeapPageId) pid, data);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (randomAccessFile != null) randomAccessFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        throw new IllegalStateException("readPage() in HeapFile unreachable");//Exception
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        int pageNumber = ((HeapPage) page).getId().getPageNumber();
        byte[] pageData = page.getPageData();
        //page.gatPageData() is used to serialize
        RandomAccessFile randomAccessFile = null;
        try {
            randomAccessFile = new RandomAccessFile(f, "rw");
            randomAccessFile.seek(pageNumber * BufferPool.getPageSize());
            randomAccessFile.write(pageData, 0, pageData.length);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (randomAccessFile != null) {
                    randomAccessFile.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        return (int) f.length() / BufferPool.getPageSize();
    }


    // see DbFile.java for javadocs
    public ArrayList<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException,IOException, TransactionAbortedException {
        ArrayList<Page> dirtyPage = new ArrayList<>();
        for (int i = 0; i < numPages(); i++) {
            Page page = Database.getBufferPool().getPage(tid, new HeapPageId(getId(), i), Permissions.READ_WRITE);
            if (((HeapPage) page).getNumEmptySlots() > 0) {
                ((HeapPage) page).insertTuple(t);
                //这个返回值到底是什么意思呢？被修改的页？
                dirtyPage.add(page);
                return dirtyPage;
            }
        }
        //如果当前File所Page都满了，就要添加页
        {
            PageId heapPageId = new HeapPageId(getId(), numPages());
            Page newHeapPage = null;
            FileOutputStream fileOutputStream = null;
            try {
                newHeapPage = new HeapPage((HeapPageId) heapPageId, HeapPage.createEmptyPageData());
                fileOutputStream = new FileOutputStream(f, true);
                fileOutputStream.write(newHeapPage.getPageData(), 0, BufferPool.getPageSize());
                newHeapPage = Database.getBufferPool().getPage(tid, heapPageId, Permissions.READ_WRITE);
                ((HeapPage) newHeapPage).insertTuple(t);
                dirtyPage.add(newHeapPage);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (fileOutputStream != null) {
                        fileOutputStream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return dirtyPage;
        }
    }

    // Removes the specified tuple from the file on behalf of the specified transaction.
    // This method will acquire a lock on the affected pages of the file, and may block
    // until the lock can be acquired.
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        ArrayList<Page> dirtyPage = new ArrayList<>();
        RecordId recordId = t.getRecordId();
        PageId pageId = recordId.getPageId();
        if (getId() != pageId.getTableId()) {
            throw new DbException("This tuple is not subsist in this File");
        }
        //pageNumber是从0开始
        int pageNumber = pageId.getPageNumber();
        if(pageNumber >= numPages()){
            throw new DbException("This tuple subsist in a nonexistence page");
        }
        Page page = Database.getBufferPool().getPage(tid, new HeapPageId(getId(), pageNumber), Permissions.READ_WRITE);
        ((HeapPage) page).deleteTuple(t);
        return dirtyPage;
    }

    // see DbFile.java for javadocs
    public AbstractDbFileIterator iterator(TransactionId tid) {
        return new HeapFileIterator(tid);
    }

    private class HeapFileIterator extends AbstractDbFileIterator {
        TransactionId tid;
        Iterator<Tuple> currentIterator;
        int pageNum;

        HeapFileIterator(TransactionId tid) {
            this.tid = tid;
        }

        @Override
        public void open() throws DbException, TransactionAbortedException {
            if (currentIterator == null) {
                this.pageNum = 0;
                currentIterator = ((HeapPage) Database.getBufferPool().getPage(tid, new HeapPageId(f.getAbsoluteFile().hashCode(), pageNum), Permissions.READ_ONLY)).iterator();
            }
        }

        @Override
        public boolean hasNext() throws DbException, TransactionAbortedException {
            while (currentIterator != null) {
                if (currentIterator.hasNext()) return true;
                if (pageNum >= numPages() - 1) {
                    return false;
                }
                currentIterator = ((HeapPage) Database.getBufferPool().getPage(tid, new HeapPageId(f.getAbsoluteFile().hashCode(), ++pageNum), Permissions.READ_ONLY)).iterator();
            }
            return false;
        }

        @Override
        public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
            if (currentIterator != null) {
                return currentIterator.next();
            }
            throw new NoSuchElementException();
        }

        @Override
        public void rewind() throws DbException, TransactionAbortedException {
            currentIterator = null;
            open();
        }

        @Override
        public void close() {
            currentIterator = null;
        }

        @Override
        protected Tuple readNext() throws DbException, TransactionAbortedException {
            //Unrealized
            return null;
        }
    }
}

