package simpledb;

import java.util.HashMap;

/**
 * Knows how to compute some aggregate over a set of IntFields.
 */
public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;

    /**
     * Aggregate constructor
     *
     * @param gbfield
     * the 0-based index of the group-by field in the tuple, or
     * NO_GROUPING if there is no grouping
     * @param gbfieldtype
     * the type of the group by field (e.g., Type.INT_TYPE), or null
     * if there is no grouping
     * @param afield
     * the 0-based index of the aggregate field in the tuple
     * @param what
     * the aggregation operator
     */
    private int gbfield;
    private Type[] typeArray = new Type[2];
    private int afield;
    private Op what;
    private HashMap<Field, Tuple> group;
    private HashMap<Field, Integer> count;


    public IntegerAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        this.gbfield = gbfield;
        typeArray[0] = gbfieldtype;
        this.afield = afield;
        this.what = what;
        group = new HashMap<>();
        count = new HashMap<>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     *
     * @param tup the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        if (tup.getTupleDesc().getFieldType(afield) == Type.INT_TYPE) {
            typeArray[1] = Type.INT_TYPE;
            Tuple tuple = new Tuple(new TupleDesc(typeArray));
            Field k = tup.getField(gbfield);
            tuple.setField(0, k);
            if (group.containsKey(k)) {
                count.replace(k, count.get(k) + 1);
                IntField oldValue = (IntField) group.get(k).getField(1);
                IntField newValue = (IntField) tup.getField(afield);
                if (what.toString() == "sum" || what.toString() == "avg") {
                    tuple.setField(1, new IntField(Integer.sum(oldValue.getValue(), newValue.getValue())));
                    group.replace(k, tuple);
                }
                if (what.toString() == "avg") {
                    tuple.setField(1, new IntField(Integer.sum(oldValue.getValue() * count.get(k) - 1, newValue.getValue()) / count.get(k)));
                    group.replace(k, tuple);
                }
                if (what.toString() == "min") {
                    tuple.setField(1, new IntField(Integer.min(oldValue.getValue(), newValue.getValue())));
                    group.replace(k, tuple);
                }
                if (what.toString() == "max") {
                    tuple.setField(1, new IntField(Integer.max(oldValue.getValue(), newValue.getValue())));
                    group.replace(k, tuple);
                }
            } else {
                IntField newValue = (IntField) tup.getField(afield);
                tuple.setField(1, newValue);
                group.put(k, tuple);
                count.put(k, 1);
            }
        }
    }


    /**
     * Create a OpIterator over group aggregate results.
     *
     * @return a OpIterator whose tuples are the pair (groupVal, aggregateVal)
     * if using group, or a single (aggregateVal) if no grouping. The
     * aggregateVal is determined by the type of aggregate specified in
     * the constructor.
     */
    public OpIterator iterator() {
        return new TupleIterator(new TupleDesc(typeArray), group.values());
    }

}
