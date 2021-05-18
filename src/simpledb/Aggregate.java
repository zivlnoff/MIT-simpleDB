package simpledb;

import java.util.NoSuchElementException;

/**
 * The Aggregation operator that computes an aggregate (e.g., sum, avg, max,
 * min). Note that we only support aggregates over a single column, grouped by a
 * single column.
 */
public class Aggregate extends Operator {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     * <p>
     * Implementation hint: depending on the type of afield, you will want to
     * construct an {@link IntegerAggregator} or {@link StringAggregator} to help
     * you with your implementation of readNext().
     *
     * @param child
     * The OpIterator that is feeding us tuples.
     * @param afield
     * The column over which we are computing an aggregate.
     * @param gfield
     * The column over which we are grouping the result, or -1 if
     * there is no grouping
     * @param aop
     * The aggregation operator to use
     */
    private OpIterator child;
    private int afield;
    private int gfield;
    private Aggregator.Op aop;
    private Aggregator aggregator;
    OpIterator iterator;

    public Aggregate(OpIterator child, int afield, int gfield, Aggregator.Op aop) {
        this.child = child;
        this.afield = afield;
        this.gfield = gfield;
        this.aop = aop;
        if (child.getTupleDesc().getFieldType(afield) == Type.INT_TYPE) {
            aggregator = new IntegerAggregator(gfield, child.getTupleDesc().getFieldType(gfield), afield, aop);
        }
        if (child.getTupleDesc().getFieldType(afield) == Type.STRING_TYPE) {
            aggregator = new StringAggregator(gfield, child.getTupleDesc().getFieldType(gfield), afield, aop);
        }
    }

    /**
     * @return If this aggregate is accompanied by a groupby, return the groupby
     * field index in the <b>INPUT</b> tuples. If not, return
     * {@link simpledb.Aggregator#NO_GROUPING}
     */
    public int groupField() {
        return gfield >= 0 ? gfield : Aggregator.NO_GROUPING;
    }

    /**
     * @return If this aggregate is accompanied by a group by, return the name
     * of the groupby field in the <b>OUTPUT</b> tuples. If not, return
     * null;
     */
    public String groupFieldName() {
        return gfield >= 0 ? child.getTupleDesc().getFieldName(gfield) : null;
    }

    /**
     * @return the aggregate field
     */
    public int aggregateField() {
        return afield;
    }

    /**
     * @return return the name of the aggregate field in the <b>OUTPUT</b>
     * tuples
     */
    public String aggregateFieldName() {
        return child.getTupleDesc().getFieldName(afield);
    }

    /**
     * @return return the aggregate operator
     */
    public Aggregator.Op aggregateOp() {
        return aop;
    }

    public static String nameOfAggregatorOp(Aggregator.Op aop) {
        return aop.toString();
    }

    public void open() throws NoSuchElementException, DbException,
            TransactionAbortedException {
        super.open();
        child.open();
        try {
            while (true)
                aggregator.mergeTupleIntoGroup(child.next());
        } catch (NoSuchElementException e) {
            // explicitly ignored
        }
        iterator = aggregator.iterator();
        iterator.open();
    }

    /**
     * Returns the next tuple. If there is a group by field, then the first
     * field is the field by which we are grouping, and the second field is the
     * result of computing the aggregate. If there is no group by field, then
     * the result tuple should contain one field representing the result of the
     * aggregate. Should return null if there are no more tuples.
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        if (iterator.hasNext()) {
            Tuple next = iterator.next();

            Tuple tuple = new Tuple(getTupleDesc());
            if (gfield == -1) {
                tuple.setField(0, next.getField(1));
                return tuple;
            }
            tuple.setField(0, next.getField(0));
            tuple.setField(1, next.getField(1));
            return tuple;
        }
        else {
            return null;
        }
    }

    public void rewind() throws DbException, TransactionAbortedException {
        iterator.rewind();
    }

    /**
     * Returns the TupleDesc of this Aggregate. If there is no group by field,
     * this will have one field - the aggregate column. If there is a group by
     * field, the first field will be the group by field, and the second will be
     * the aggregate value column.
     * <p>
     * The name of an aggregate column should be informative. For example:
     * "aggName(aop) (child_td.getFieldName(afield))" where aop and afield are
     * given in the constructor, and child_td is the TupleDesc of the child
     * iterator.
     */
    public TupleDesc getTupleDesc() {
        if (gfield == -1) {
            Type[] types = new Type[1];
            types[0] = child.getTupleDesc().getFieldType(afield);
            return new TupleDesc(types, new String[]{aop.toString() + "(" + child.getTupleDesc().getFieldName(afield) + ")"});
        }
        Type[] types = new Type[2];
        types[0] = child.getTupleDesc().getFieldType(gfield);
        types[1] = switch (aop.toString()) {
            case "avg","count","sum" -> Type.INT_TYPE;
            case "max","min" -> child.getTupleDesc().getFieldType(afield);
            default -> throw new RuntimeException("状态不正确");
        };
        return new TupleDesc(types, new String[]{child.getTupleDesc().getFieldName(gfield), aop.toString() + "(" + child.getTupleDesc().getFieldName(afield) + ")"});
    }

    public void close() {
        super.close();
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
