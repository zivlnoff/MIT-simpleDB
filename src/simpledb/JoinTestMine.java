package simpledb;

import java.io.File;
import java.io.IOException;

public class JoinTestMine {
    public static void main(String[] argv) { // construct a 3-column table schema
        Type types[] = new Type[]{Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE};
        String names[] = new String[]{"field0", "field1", "field2"};
        TupleDesc td = new TupleDesc(types, names);
        // create the tables, associate them with the data files
        // and tell the catalog about the schema the tables.
        try {
            HeapFileEncoder.convert(new File("data.txt"), new File("data.dat"), BufferPool.getPageSize(), 3, types, ',');
            HeapFileEncoder.convert(new File("JoinTestMine2.dat"), new File("JoinTestMine2_binary.dat"), BufferPool.getPageSize(), 3, types, ',');
        } catch (IOException e) {
            e.printStackTrace();
        }
        HeapFile table1 = new HeapFile(new File("JoinTestMine1_binary.dat"), td);
        Database.getCatalog().addTable(table1, "tt1");
        HeapFile table2 = new HeapFile(new File("JoinTestMine2_binary.dat"), td);
        Database.getCatalog().addTable(table2, "tt2");
        // construct the query: we use two SeqScans, which spoonfeed
        // tuples via iterators into join
        TransactionId tid = new TransactionId();
        SeqScan ss1 = new SeqScan(tid, table1.getId(), "t1");
        SeqScan ss2 = new SeqScan(tid, table2.getId(), "t2");
        System.out.println(ss1.getTableName());
        System.out.println(ss1.getAlias());
        System.out.println(ss1.getTupleDesc());
        // create a filter for the where condition
        Filter sf1 = new Filter(new Predicate(0, Predicate.Op.GREATER_THAN, new IntField(1)), ss1);
        JoinPredicate p = new JoinPredicate(0, Predicate.Op.EQUALS, 1);
        Join j = new Join(p, sf1, ss2);
        // and run it
        try {
            j.open();
            while (j.hasNext()) {
                Tuple tup = j.next();
                System.out.println(tup);
            }
            j.close();
            Database.getBufferPool().transactionComplete(tid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}