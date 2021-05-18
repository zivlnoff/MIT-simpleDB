package simpledb;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

/**
 * Tuple maintains information about the contents of a tuple. Tuples have a
 * specified schema specified by a TupleDesc object and contain Field objects
 * with the data for each field.
 */
public class Tuple implements Serializable {

    private static final long serialVersionUID = 1L;
    private TupleDesc td = null;
    private ArrayList<Field> t;

    /**
     * Create a new tuple with the specified schema (type).
     *
     * @param td the schema of this tuple. It must be a valid TupleDesc
     *           instance with at least one field.
     */
    public Tuple(TupleDesc td) {
        this.td = td;
        t = new ArrayList<>(td.numFields());
    }

    /**
     * @return The TupleDesc representing the schema of this tuple.
     */
    public TupleDesc getTupleDesc() {
        return td;
    }

    /**
     * @return The RecordId representing the location of this tuple on disk. May
     * be null.
     */
    private RecordId rid = null;

    public RecordId getRecordId() {
        return rid;
    }

    /**
     * Set the RecordId information for this tuple.
     *
     * @param rid the new RecordId for this tuple.
     */
    public void setRecordId(RecordId rid) {
        this.rid = rid;
    }

    /**
     * Change the value of the ith field of this tuple.
     *
     * @param i index of the field to change. It must be a valid index.
     * @param f new value for the field.
     */
    public void setField(int i, Field f) {
        if (td.getFieldType(i) == f.getType()) {
            t.add(i, f);
        }
    }

    /**
     * @param i field index to return. Must be a valid index.
     * @return the value of the ith field, or null if it has not been set.
     */
    public Field getField(int i) {
        return t.get(i);
    }

    /**
     * Returns the contents of this Tuple as a string. Note that to pass the
     * system tests, the format needs to be as follows:
     * <p>
     * column1\tcolumn2\tcolumn3\t...\tcolumnN
     * <p>
     * where \t is any whitespace (except a newline)
     */
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < td.numFields(); i++) {
            if (t.get(i).getClass() == IntField.class)
                stringBuilder.append(((IntField) t.get(i)).getValue()).append(" ");
            if (t.get(i).getClass() == StringField.class) {
                stringBuilder.append(((StringField) t.get(i)).getValue()).append(" ");
            }
        }
        return stringBuilder.substring(0,stringBuilder.length()-1);
    }


    /**
     * @return An iterator which iterates over all the fields of this tuple
     */
    public Iterator<Field> fields() {
        return t.iterator();
    }

    /**
     * reset the TupleDesc of this tuple (only affecting the TupleDesc)
     */
    public void resetTupleDesc(TupleDesc td) {
        // some code goes here
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tuple tuple = (Tuple) o;
        return td.equals(tuple.td) && t.equals(tuple.t) && rid.equals(tuple.rid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(td, t, rid);
    }
}
