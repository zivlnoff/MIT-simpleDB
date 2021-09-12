package simpledb;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

/**
 * Inserts tuples read from the child operator into the tableId specified in the
 * constructor
 */
public class Insert extends Operator {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     *
     * @param t
     * The transaction running the insert.
     * @param child
     * The child operator from which to read tuples to be inserted.
     * @param tableId
     * The table in which to insert tuples.
     * @throws DbException
     * if TupleDesc of child differs from table into which we are to
     * insert.
     */

    private TransactionId t;
    private OpIterator child;
    private int tableId;

    public Insert(TransactionId t, OpIterator child, int tableId)
            throws DbException {
        this.t = t;
        this.child = child;
        this.tableId = tableId;
    }

    public TupleDesc getTupleDesc() {
        return new TupleDesc(new Type[]{Type.INT_TYPE});
    }

    public void open() throws DbException, TransactionAbortedException {
        super.open();
        child.open();
    }

    public void close() {
        super.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        child.rewind();
        onceOnly = false;
    }

    /**
     * Inserts tuples read from child into the tableId specified by the
     * constructor. It returns a one field tuple containing the number of
     * inserted records. Inserts should be passed through BufferPool. An
     * instances of BufferPool is available via Database.getBufferPool(). Note
     * that insert DOES NOT need check to see if a particular tuple is a
     * duplicate before inserting it.
     *
     * @return A 1-field tuple containing the number of inserted records, or
     * null if called more than once.
     * @see Database#getBufferPool
     * @see BufferPool#insertTuple
     */
    private boolean onceOnly = false;
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        if (onceOnly == false) {
            int count = 0;
            while (child.hasNext()) {
                try {
                    Database.getBufferPool().insertTuple(t, tableId, child.next());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                count++;
            }
            Tuple tuple = new Tuple(new TupleDesc(new Type[]{Type.INT_TYPE}));
            tuple.setField(0, new IntField(count));
            onceOnly = true;
            return tuple;
        } else {
            return null;
        }
    }
    @Override
    public OpIterator[] getChildren() {
        // some code goes here
        return null;
    }

    @Override
    public void setChildren(OpIterator[] children) {
        // some code goes here
    }
}
