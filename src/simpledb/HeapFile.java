package simpledb;

import java.io.*;
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
        HeapPage heapPage = null;
        int pageSize = BufferPool.getPageSize();
        byte[] data = new byte[pageSize];
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(f));
            int skip = pid.getPageNumber() * pageSize;
            bis.skipNBytes(skip);
            bis.read(data, 0, pageSize);
            return new HeapPage((HeapPageId) pid, data);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bis != null) {
                    bis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;//Exception
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile(f, "rw");
        int pageNumber = ((HeapPage) page).getId().getPageNumber();
        byte[] pageData = page.getPageData();
        randomAccessFile.seek(pageNumber * BufferPool.getPageSize());
        randomAccessFile.write(pageData, 0, pageData.length);
        randomAccessFile.close();
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        return (int) f.length() / BufferPool.getPageSize();
    }


    // see DbFile.java for javadocs
    public ArrayList<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        ArrayList<Page> ret = new ArrayList<>();
        ArrayList<Page> arrayList = new ArrayList<>();
        for (int i = 0; i < numPages(); i++) {
            arrayList.add((HeapPage) Database.getBufferPool().getPage(tid, new HeapPageId(getId(), i), Permissions.READ_WRITE));
        }
        for (Page heapPage : arrayList) {
            if (((HeapPage) heapPage).getNumEmptySlots() > 0) {
                ((HeapPage) heapPage).insertTuple(t);
                ret.add(heapPage);
                return ret;
            }
        }
        PageId heapPageId = new HeapPageId(getId(), numPages());
        Page newHeapPage = new HeapPage((HeapPageId) heapPageId, HeapPage.createEmptyPageData());
        FileOutputStream fileOutputStream = new FileOutputStream(f, true);
        fileOutputStream.write(newHeapPage.getPageData(), 0, BufferPool.getPageSize());
        newHeapPage = Database.getBufferPool().getPage(tid, heapPageId, Permissions.READ_WRITE);
        ((HeapPage)newHeapPage).insertTuple(t);
        ret.add(newHeapPage);
        fileOutputStream.close();
        return ret;
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        ArrayList<Page> arrayList = new ArrayList<>();
        for (int i = 0; i < numPages(); i++) {
            arrayList.add(Database.getBufferPool().getPage(tid, new HeapPageId(getId(), i), Permissions.READ_WRITE));
        }
        for (Page heapPage : arrayList) {
            ((HeapPage) heapPage).deleteTuple(t);
        }
        return arrayList;
    }

    // see DbFile.java for javadocs
    public AbstractDbFileIterator iterator(TransactionId tid) {
        return new AbstractDbFileIterator() {
            Iterator<Tuple> currentIterator;
            int pageNum = 0;

            @Override
            public void open() throws DbException, TransactionAbortedException {
                currentIterator = ((HeapPage) Database.getBufferPool().getPage(tid, new HeapPageId(f.getAbsoluteFile().hashCode(), pageNum), Permissions.READ_ONLY)).iterator();
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
                if (currentIterator == null)
                    throw new IllegalStateException();
                pageNum = 0;
                currentIterator = ((HeapPage) Database.getBufferPool().getPage(tid, new HeapPageId(f.getAbsoluteFile().hashCode(), pageNum), null)).iterator();
            }

            @Override
            public void close() {
                currentIterator = null;
            }

            //总感觉后面会用到,先不做修改，更加便捷
            @Override
            protected Tuple readNext() throws DbException, TransactionAbortedException {
                return null;
            }
        };
    }

}

