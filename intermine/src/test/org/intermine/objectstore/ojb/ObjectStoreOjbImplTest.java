package org.flymine.objectstore.ojb;

import junit.framework.TestCase;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.math.BigDecimal;

import org.apache.ojb.broker.metadata.DescriptorRepository;

import org.flymine.objectstore.ObjectStore;
import org.flymine.objectstore.ObjectStoreAbstractImpl;
import org.flymine.objectstore.ObjectStoreException;
import org.flymine.objectstore.ObjectStoreFactory;
import org.flymine.objectstore.ObjectStoreTestCase;
import org.flymine.objectstore.proxy.LazyReference;
import org.flymine.objectstore.query.Query;
import org.flymine.objectstore.query.Results;
import org.flymine.objectstore.query.ResultsRow;
import org.flymine.objectstore.query.QueryClass;
import org.flymine.objectstore.query.QueryCollectionReference;
import org.flymine.objectstore.query.QueryField;
import org.flymine.objectstore.query.QueryValue;
import org.flymine.objectstore.query.QueryFunction;
import org.flymine.objectstore.query.SimpleConstraint;
import org.flymine.objectstore.query.ContainsConstraint;
import org.flymine.sql.query.ExplainResult;

import org.flymine.model.testmodel.*;

public class ObjectStoreOjbImplTest extends ObjectStoreTestCase
{
    public ObjectStoreOjbImplTest(String arg) {
        super(arg);
    }

    public void setUp() throws Exception {
        super.setUp();
        os = (ObjectStoreAbstractImpl) ObjectStoreFactory.getObjectStore("os.unittest");
        PersistenceBrokerFlyMineImpl pb = (PersistenceBrokerFlyMineImpl) ((ObjectStoreOjbImpl) os).getPersistenceBroker();
        db = pb.getDatabase();
        DescriptorRepository dr = pb.getDescriptorRepository();
        writer = new ObjectStoreWriterOjbImpl((ObjectStoreOjbImpl) os);
        storeData();
        // clear the cache to ensure that objects are materialised later (in case broker reused)
        ((ObjectStoreWriterOjbImpl) writer).pb.clearCache();
    }

    public void tearDown() throws Exception {
        removeDataFromStore();
    }

    public void testCEOWhenSearchingForManager() throws Exception {
        QueryClass c1 = new QueryClass(Manager.class);
        Query q1 = new Query();
        q1.addFrom(c1);
        q1.addToSelect(c1);
        QueryField f1 = new QueryField(c1, "name");
        QueryValue v1 = new QueryValue("EmployeeB1");
        SimpleConstraint sc1 = new SimpleConstraint(f1, SimpleConstraint.EQUALS, v1);
        q1.setConstraint(sc1);
        List l1 = os.execute(q1, 0, 10);
        //System.out.println(l1.toString());
        //System.out.println(l1.get(0).getClass());
        //System.out.println(((ResultsRow) l1.get(0)).get(0).getClass());
        CEO ceo = (CEO) (((ResultsRow) l1.get(0)).get(0));
        //System.out.println(ceo.getSalary());
        assertEquals(45000, ceo.getSalary());
    }

    public void testResults() throws Exception {
        Object[][] r = new Object[][] { { data.get("CompanyA") },
                                        { data.get("CompanyB") } };
        List res = os.execute((Query) queries.get("SelectSimpleObject"));
        assertEquals(toList(r).size(), res.size());
        assertEquals(toList(r), res);
    }

    public void testLazyCollection() throws Exception {
        List r = os.execute((Query) queries.get("ContainsN1"));
        Department d = (Department) ((ResultsRow) r.get(0)).get(0);
        List e = d.getEmployees();
        assertTrue(e instanceof Results);

        List expected = new ArrayList();
        expected.add(data.get("EmployeeA1"));
        expected.add(data.get("EmployeeA2"));
        expected.add(data.get("EmployeeA3"));
        assertEquals(expected, e);

        // navigate from a lazy collection to a field that is a lazy reference
        Address a = ((Employee) e.get(0)).getAddress();
        assertTrue(a instanceof LazyReference);
        assertEquals(a, ((Employee) data.get("EmployeeA1")).getAddress());
    }


    public void testLazyCollectionMtoN() throws Exception {
        // query for company and check contractors
        QueryClass c1 = new QueryClass(Company.class);
        Query q1 = new Query();
        q1.addFrom(c1);
        q1.addToSelect(c1);
        QueryField f1 = new QueryField(c1, "name");
        QueryValue v1 = new QueryValue("CompanyA");
        SimpleConstraint sc1 = new SimpleConstraint(f1, SimpleConstraint.EQUALS, v1);
        q1.setConstraint(sc1);
        Results r  = os.execute(q1);
        ResultsRow rr = (ResultsRow) r.get(0);
        Company c = (Company) rr.get(0);
        List contractors = c.getContractors();
        assertTrue(contractors instanceof Results);
        List expected1 = new ArrayList();
        expected1.add(data.get("ContractorA"));
        expected1.add(data.get("ContractorB"));
        assertEquals(contractors, expected1);

        List companies = ((Contractor) contractors.get(0)).getCompanys();
        assertTrue(companies instanceof Results);
        List expected2 = new ArrayList();
        expected2.add(data.get("CompanyA"));
        expected2.add(data.get("CompanyB"));
        assertEquals(companies, expected2);
    }


    public void testLazyReference() throws Exception {
        QueryClass c1 = new QueryClass(Department.class);
        Query q1 = new Query();
        q1.addFrom(c1);
        q1.addToSelect(c1);
        QueryField f1 = new QueryField(c1, "name");
        QueryValue v1 = new QueryValue("DepartmentA1");
        SimpleConstraint sc1 = new SimpleConstraint(f1, SimpleConstraint.EQUALS, v1);
        q1.setConstraint(sc1);
        Results r  = os.execute(q1);
        ResultsRow rr = (ResultsRow) r.get(0);
        Department d = (Department) rr.get(0);
        Company c = d.getCompany();
        assertTrue(c instanceof org.flymine.objectstore.proxy.LazyReference);
        assertTrue(c.equals(data.get("CompanyA")));
        assertTrue(data.get("CompanyA").equals(c));
    }


    public void testLazyCollectionRef() throws Exception {
        QueryClass c1 = new QueryClass(Department.class);
        Query q1 = new Query();
        q1.addFrom(c1);
        q1.addToSelect(c1);
        QueryField f1 = new QueryField(c1, "name");
        QueryValue v1 = new QueryValue("DepartmentA1");
        SimpleConstraint sc1 = new SimpleConstraint(f1, SimpleConstraint.EQUALS, v1);
        q1.setConstraint(sc1);
        Results r  = os.execute(q1);
        ResultsRow rr = (ResultsRow) r.get(0);
        Department d = (Department) rr.get(0);
        List e = d.getEmployees();
        assertTrue(e instanceof Results);

        List expected = new ArrayList();
        expected.add(data.get("EmployeeA1"));
        expected.add(data.get("EmployeeA2"));
        expected.add(data.get("EmployeeA3"));

        // test that we can navigate from member of lazy collection to one of its lazy references
        assertEquals(expected, e);
        Employee e1 = (Employee) e.get(0);
        Address a = e1.getAddress();
        assertTrue(a instanceof org.flymine.objectstore.proxy.LazyReference);
        assertEquals(a, ((Employee) data.get("EmployeeA1")).getAddress());
    }

    public void testLazyReferenceRef() throws Exception {

        QueryClass c1 = new QueryClass(Department.class);
        Query q1 = new Query();
        q1.addFrom(c1);
        q1.addToSelect(c1);
        QueryField f1 = new QueryField(c1, "name");
        QueryValue v1 = new QueryValue("DepartmentA1");
        SimpleConstraint sc1 = new SimpleConstraint(f1, SimpleConstraint.EQUALS, v1);
        q1.setConstraint(sc1);
        List r  = os.execute(q1);
        Department d = (Department) ((ResultsRow) r.get(0)).get(0);

        Company c = d.getCompany();
        assertTrue(c instanceof LazyReference);
        assertEquals(data.get("CompanyA"), c);

        // navigate from a lazy reference to a field that is a lazy reference
        Address a = c.getAddress();
        assertTrue(a instanceof LazyReference);
        assertEquals(a, ((Company) data.get("CompanyA")).getAddress());
    }

    public void testCountNoGroupByNotDistinct() throws Exception {
        Query q = (Query) queries.get("ContainsDuplicatesMN");
        q.setDistinct(false);
        int count = os.count(q);
        assertEquals(count, 8);
    }

    public void testCountNoGroupByDistinct() throws Exception {
        Query q = (Query) queries.get("ContainsDuplicatesMN");
        q.setDistinct(true);
        int count = os.count(q);
        assertEquals(count, 4);
    }

   public void testCountGroupByNotDistinct() throws Exception {
        Query q = (Query) queries.get("SimpleGroupBy");
        q.setDistinct(false);
        int count = os.count(q);
        assertEquals(count, 2);
    }


    // distinct doesn't actually do anything to group by reuslt
    public void testCountGroupByDistinct() throws Exception {
        Query q = (Query) queries.get("SimpleGroupBy");
        q.setDistinct(true);
        int count = os.count(q);
        assertEquals(count, 2);
    }

}
