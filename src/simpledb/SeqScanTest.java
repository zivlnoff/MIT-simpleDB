package simpledb;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import simpledb.systemtest.SystemTestUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author ColvTZzi
 * @create 2021-04-27-15:47
 */
public class SeqScanTest {
    private HeapFile hf;
    private TransactionId tid;
    private TupleDesc td;

    /**
     * Set up initial resources for each unit test.
     */
    @Before
    public void setUp() throws Exception {
        hf = SystemTestUtil.createRandomHeapFile(2 , 20, null, null);
        td = Utility.getTupleDesc(3);
        tid = new TransactionId();
    }

    @After
    public void tearDown() throws Exception {
        Database.getBufferPool().transactionComplete(tid);
    }

    @Test
    public void test(){
        // construct a 3-column table schema
        Type types[] = new Type[]{ Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE };
        String names[] = new String[]{ "field0", "field1", "field2" };
        TupleDesc descriptor = new TupleDesc(types, names);

        File file = new File("some_data_file.dat");
        File binaryFile = new File("some_data_file_binary.dat");
        try {
            HeapFileEncoder.convert(file,binaryFile,BufferPool.getPageSize(),3);
        } catch (IOException e) {
            e.printStackTrace();
        }
        HeapFile table1 = new HeapFile(new File("some_data_file_binary.dat"), descriptor);
        Database.getCatalog().addTable(table1, "test");

        // construct the query: we use a simple SeqScan, which spoonfeeds
        // tuples via its iterator.
        TransactionId tid = new TransactionId();
        SeqScan f = new SeqScan(tid, table1.getId());

        try {
            // and run it
            f.open();
            while (f.hasNext()) {
                Tuple tup = f.next();
                System.out.println(tup);
            }
            f.close();
            Database.getBufferPool().transactionComplete(tid);
        } catch (Exception e) {
            System.out.println ("Exception : " + e);
        }
    }
}
