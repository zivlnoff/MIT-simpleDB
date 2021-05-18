package simpledb;

import java.io.IOException;

/**
 * The delete operator. Delete reads tuples from its child operator and removes
 * them from the table they belong to.
 */
public class Delete extends Operator {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor specifying the transaction that this delete belongs to as
     * well as the child to read from.
     *
     * @param t
     * The transaction this delete runs in
     * @param child
     * The child operator from which to read tuples for deletion
     */
    private TransactionId t;
    private OpIterator child;

    public Delete(TransactionId t, OpIterator child) {
        this.t = t;
        this.child = child;
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
    }

    /**
     * Deletes tuples as they are read from the child operator. Deletes are
     * processed via the buffer pool (which can be accessed via the
     * Database.getBufferPool() method.
     *
     * @return A 1-field tuple containing the number of deleted records.
     * @see Database#getBufferPool
     * @see BufferPool#deleteTuple
     */
    private boolean onceOnly = false;

    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        if (onceOnly == false) {
            {
                int count = 0;
                while (child.hasNext()) {
                    try {
                        Database.getBufferPool().deleteTuple(t, child.next());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    count++;
                }
                Tuple tuple = new Tuple(new TupleDesc(new Type[]{Type.INT_TYPE}));
                tuple.setField(0, new IntField(count));
                onceOnly = true;
                return tuple;
            }
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
