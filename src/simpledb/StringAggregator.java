package simpledb;

import java.util.HashMap;

/**
 * Knows how to compute some aggregate over a set of StringFields.
 */
public class StringAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;

    /**
     * Aggregate constructor
     *
     * @param gbfield     the 0-based index of the group-by field in the tuple, or NO_GROUPING if there is no grouping
     * @param gbfieldtype the type of the group by field (e.g., Type.INT_TYPE), or null if there is no grouping
     * @param afield      the 0-based index of the aggregate field in the tuple
     * @param what        aggregation operator to use -- only supports COUNT
     * @throws IllegalArgumentException if what != COUNT
     */

    private int gbfield;
    private Type[] typeArray = new Type[2];
    private int afield;
    private Op what;
    private HashMap<Field, Tuple> group;
    private HashMap<Field, Integer> count;

    public StringAggregator(int gbfield, Type gbfieldtype, int afield, Op what) {
        this.gbfield = gbfield;
        typeArray[0] = gbfieldtype;
        typeArray[1] = Type.INT_TYPE;
        this.afield = afield;
        this.what = what;
        group = new HashMap<>();
        count = new HashMap<>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the constructor
     *
     * @param tup the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        if (tup.getTupleDesc().getFieldType(afield) == Type.STRING_TYPE) {
            Tuple tuple = new Tuple(new TupleDesc(typeArray));
            IntField k = (IntField) tup.getField(gbfield);
            tuple.setField(0, k);
            if (count.containsKey(k)) {
                count.replace(k, count.get(k) + 1);
            } else {
                count.put(k, 1);
            }
            tuple.setField(1, new IntField(count.get(k)));
            if (group.containsKey(k)) {
                group.replace(k, tuple);
            }
            else {
                group.put(k, tuple);
            }
        }
    }

    /**
     * Create a OpIterator over group aggregate results.
     *
     * @return a OpIterator whose tuples are the pair (groupVal,
     * aggregateVal) if using group, or a single (aggregateVal) if no
     * grouping. The aggregateVal is determined by the type of
     * aggregate specified in the constructor.
     */
    public OpIterator iterator() {
        return new TupleIterator(new TupleDesc(typeArray), group.values());
    }

}
