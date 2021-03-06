/*
 * Copyright 2011 James Moger.
 * Copyright 2012 Frédéric Gaillard.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.iciql.test;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.iciql.Constants;
import com.iciql.Db;
import com.iciql.Iciql.Mode;
import com.iciql.test.DataTypeAdapterTest.SerializedObjectTypeAdapterTest;
import com.iciql.test.models.BooleanModel;
import com.iciql.test.models.CategoryAnnotationOnly;
import com.iciql.test.models.ComplexObject;
import com.iciql.test.models.Customer;
import com.iciql.test.models.DefaultValuesModel;
import com.iciql.test.models.EnumModels.EnumIdModel;
import com.iciql.test.models.EnumModels.EnumOrdinalModel;
import com.iciql.test.models.EnumModels.EnumStringModel;
import com.iciql.test.models.MultipleBoolsModel;
import com.iciql.test.models.Order;
import com.iciql.test.models.PrimitivesModel;
import com.iciql.test.models.Product;
import com.iciql.test.models.ProductAnnotationOnly;
import com.iciql.test.models.ProductAnnotationOnlyWithForeignKey;
import com.iciql.test.models.ProductInheritedAnnotation;
import com.iciql.test.models.ProductMixedAnnotation;
import com.iciql.test.models.ProductView;
import com.iciql.test.models.ProductViewFromQuery;
import com.iciql.test.models.ProductViewInherited;
import com.iciql.test.models.ProductViewInheritedComplex;
import com.iciql.test.models.SupportedTypes;
import com.iciql.util.IciqlLogger;
import com.iciql.util.IciqlLogger.IciqlListener;
import com.iciql.util.IciqlLogger.StatementType;
import com.iciql.util.StringUtils;
import com.iciql.util.Utils;
import org.apache.commons.dbcp.ConnectionFactory;
import org.apache.commons.dbcp.DriverManagerConnectionFactory;
import org.apache.commons.dbcp.PoolableConnectionFactory;
import org.apache.commons.dbcp.PoolingDataSource;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.derby.drda.NetworkServerControl;
import org.hsqldb.persist.HsqlProperties;
import org.junit.Assert;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JUnit 4 iciql test suite.
 * <p>
 * By default this test suite will run against the H2 database. You can change
 * this by switching the DEFAULT_TEST_DB value.
 * <p>
 * Alternatively, you can run this class an application which will run all tests
 * for all tested database configurations.
 * <p>
 * NOTE: If you want to test against MySQL or PostgreSQL you must create an
 * "iciql" database and allow user "sa" password "sa" complete control of that
 * database.
 */
@RunWith(Suite.class)
@SuiteClasses({AliasMapTest.class, AnnotationsTest.class, BooleanModelTest.class, ClobTest.class,
        ConcurrencyTest.class, EnumsTest.class, ModelsTest.class, PrimitivesTest.class, OneOfTest.class,
        RuntimeQueryTest.class, SamplesTest.class, UpdateTest.class, UpgradesTest.class, JoinTest.class,
        UUIDTest.class, ViewsTest.class, ForeignKeyTest.class, TransactionTest.class, NestedConditionsTest.class,
        DataTypeAdapterTest.class, ProductDaoTest.class, BitwiseConditionTest.class})
public class IciqlSuite {
    private final static File baseFolder = new File(System.getProperty("user.dir"), "/testdbs");
    private static final TestDb[] TEST_DBS = {
            new TestDb("H2", "memory", "jdbc:h2:mem:iciql"),
            new TestDb("H2", "file", "jdbc:h2:file:"
                    + new File(baseFolder, "/h2/iciql").getAbsolutePath()),
            new TestDb("H2", "tcp", "jdbc:h2:tcp://localhost/"
                    + new File(baseFolder, "/h2tcp/iciql").getAbsolutePath()),
            new TestDb("HSQL", "memory", "jdbc:hsqldb:mem:iciql"),
            new TestDb("HSQL", "file", "jdbc:hsqldb:file:testdbs/hsql/iciql"),
            new TestDb("HSQL", "tcp", "jdbc:hsqldb:hsql://localhost/iciql"),
            new TestDb("Derby", "memory", "jdbc:derby:memory:iciql;create=true"),
            new TestDb("Derby", "file", "jdbc:derby:directory:testdbs/derby/iciql;create=true"),
            new TestDb("Derby", "tcp", "jdbc:derby://localhost:1527/testdbs/derby/iciql;create=true", "sa", "sa"),
            new TestDb("MySQL", "tcp", "jdbc:mysql://localhost:3306/iciql?useSSL=false&nullNamePatternMatchesAll=true&nullCatalogMeansCurrent=true", "sa", "sa"),
            new TestDb("PostgreSQL", "tcp", "jdbc:postgresql://localhost:5432/iciql", "sa", "sa"),

            //
            // SQLite Memory
            //
            new TestDb("SQLite", "memory", "jdbc:sqlite:file::memory:?cache=shared&foreign_keys=ON"),

            //
            // SQLite DELETE rollback journal (default)
            //
            new TestDb("SQLite", "delete,full_sync", "jdbc:sqlite:"
                    + new File(baseFolder, "/sqlite/iciql.db").getAbsolutePath()
                    + "?foreign_keys=ON&journal_mode=DELETE&synchronous=FULL"),

            new TestDb("SQLite", "delete,norm_sync", "jdbc:sqlite:"
                    + new File(baseFolder, "/sqlite/iciql.db").getAbsolutePath()
                    + "?foreign_keys=ON&journal_mode=DELETE&synchronous=NORMAL"),

            new TestDb("SQLite", "delete,no_sync", "jdbc:sqlite:"
                    + new File(baseFolder, "/sqlite/iciql.db").getAbsolutePath()
                    + "?foreign_keys=ON&journal_mode=DELETE&synchronous=OFF"),

            //
            // SQLite WAL
            //
            new TestDb("SQLite", "wal,full_sync", "jdbc:sqlite:"
                    + new File(baseFolder, "/sqlite/iciql.db").getAbsolutePath()
                    + "?foreign_keys=ON&journal_mode=WAL&synchronous=FULL"),

            new TestDb("SQLite", "wal,norm_sync", "jdbc:sqlite:"
                    + new File(baseFolder, "/sqlite/iciql.db").getAbsolutePath()
                    + "?foreign_keys=ON&journal_mode=WAL&synchronous=NORMAL"),

            new TestDb("SQLite", "wal,no_sync", "jdbc:sqlite:"
                    + new File(baseFolder, "/sqlite/iciql.db").getAbsolutePath()
                    + "?foreign_keys=ON&journal_mode=WAL&synchronous=OFF"),

    };

    private static final TestDb DEFAULT_TEST_DB = TEST_DBS[3];

    private static final PrintStream ERR = System.err;

    private static PrintStream out = System.out;

    private static Map<String, PoolableConnectionFactory> connectionFactories = Utils
            .newSynchronizedHashMap();

    private static Map<String, PoolingDataSource> dataSources = Utils.newSynchronizedHashMap();

    public static void assertStartsWith(String value, String startsWith) {
        Assert.assertTrue(MessageFormat.format("Expected \"{0}\", got: \"{1}\"", startsWith, value),
                value.startsWith(startsWith));
    }

    public static void assertEqualsIgnoreCase(String expected, String actual) {
        Assert.assertTrue(MessageFormat.format("Expected \"{0}\", got: \"{1}\"", expected, actual),
                expected.equalsIgnoreCase(actual));
    }

    public static boolean equivalentTo(double expected, double actual) {
        if (Double.compare(expected, actual) == 0) {
            return true;
        }
        return Math.abs(expected - actual) <= 0.000001d;
    }

    public static Db openNewDb() {
        return openNewDb(Mode.PROD);
    }

    /**
     * Open a new Db object. All connections are cached and re-used to eliminate
     * embedded database startup costs.
     *
     * @param mode
     * @return a fresh Db object
     */
    public static Db openNewDb(Mode mode) {
        String testUrl = System.getProperty("iciql.url", DEFAULT_TEST_DB.url);
        String testUser = System.getProperty("iciql.user", DEFAULT_TEST_DB.username);
        String testPassword = System.getProperty("iciql.password", DEFAULT_TEST_DB.password);

        Db db = null;
        PoolingDataSource dataSource = dataSources.get(testUrl);
        if (dataSource == null) {
            ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(testUrl, testUser,
                    testPassword);
            GenericObjectPool pool = new GenericObjectPool();
            pool.setWhenExhaustedAction(GenericObjectPool.WHEN_EXHAUSTED_GROW);
            PoolableConnectionFactory factory = new PoolableConnectionFactory(connectionFactory, pool, null,
                    null, false, true);
            dataSource = new PoolingDataSource(pool);
            dataSources.put(testUrl, dataSource);
            connectionFactories.put(testUrl, factory);
        }
        db = Db.open(dataSource, mode);

        // drop views
        db.dropView(ProductView.class);
        db.dropView(ProductViewInherited.class);
        db.dropView(ProductViewFromQuery.class);
        db.dropView(ProductViewInheritedComplex.class);

        // drop tables
        db.dropTable(BooleanModel.class);
        db.dropTable(ComplexObject.class);
        db.dropTable(Customer.class);
        db.dropTable(DefaultValuesModel.class);
        db.dropTable(EnumIdModel.class);
        db.dropTable(EnumOrdinalModel.class);
        db.dropTable(EnumStringModel.class);
        db.dropTable(Order.class);
        db.dropTable(PrimitivesModel.class);
        db.dropTable(Product.class);
        db.dropTable(ProductAnnotationOnly.class);
        db.dropTable(ProductInheritedAnnotation.class);
        db.dropTable(ProductMixedAnnotation.class);
        db.dropTable(SupportedTypes.class);
        db.dropTable(JoinTest.UserId.class);
        db.dropTable(JoinTest.UserNote.class);
        db.dropTable(EnumsTest.BadEnums.class);
        db.dropTable(MultipleBoolsModel.class);
        db.dropTable(ProductAnnotationOnlyWithForeignKey.class);
        db.dropTable(CategoryAnnotationOnly.class);
        db.dropTable(SerializedObjectTypeAdapterTest.class);
        db.dropTable(ClobTest.StringRecord.class);

        return db;
    }

    /**
     * Open the current database.
     *
     * @return the current database
     */
    public static Db openCurrentDb() {
        String testUrl = System.getProperty("iciql.url", DEFAULT_TEST_DB.url);
        String testUser = System.getProperty("iciql.user", DEFAULT_TEST_DB.username);
        String testPassword = System.getProperty("iciql.password", DEFAULT_TEST_DB.password);
        return Db.open(testUrl, testUser, testPassword);
    }

    /**
     * Returns the name of the underlying database engine for the Db object.
     *
     * @param db
     * @return the database engine name
     */
    public static String getDatabaseEngineName(Db db) {
        String database = "";
        try {
            database = db.getConnection().getMetaData().getDatabaseProductName();
        } catch (SQLException s) {
        }
        return database;
    }

    /**
     * Returns true if the underlying database engine is Derby.
     *
     * @param db
     * @return true if underlying database engine is Derby
     */
    public static boolean isDerby(Db db) {
        return IciqlSuite.getDatabaseEngineName(db).equals("Apache Derby");
    }

    /**
     * Returns true if the underlying database engine is H2.
     *
     * @param db
     * @return true if underlying database engine is H2
     */
    public static boolean isH2(Db db) {
        return IciqlSuite.getDatabaseEngineName(db).equals("H2");
    }

    /**
     * Returns true if the underlying database engine is MySQL.
     *
     * @param db
     * @return true if underlying database engine is MySQL
     */
    public static boolean isMySQL(Db db) {
        return IciqlSuite.getDatabaseEngineName(db).equals("MySQL");
    }

    /**
     * Returns true if the underlying database engine is SQLite.
     *
     * @param db
     * @return true if underlying database engine is SQLite
     */
    public static boolean isSQLite(Db db) {
        return IciqlSuite.getDatabaseEngineName(db).equals("SQLite");
    }

    /**
     * Gets the default schema of the underlying database engine.
     *
     * @param db
     * @return the default schema
     */
    public static String getDefaultSchema(Db db) {
        if (isDerby(db)) {
            // Derby sets default schema name to username
            return "SA";
        } else if (isMySQL(db)) {
            // MySQL does not have schemas
            return null;
        } else if (isSQLite(db)) {
            // SQLite does not have schemas
            return null;
        }

        return "PUBLIC";
    }

    /**
     * Main entry point for the test suite. Executing this method will run the
     * test suite on all registered databases.
     *
     * @param args
     * @throws Exception
     */
    public static void main(String... args) throws Exception {
        Params params = new Params();
        JCommander jc = new JCommander(params);
        try {
            jc.parse(args);
        } catch (ParameterException t) {
            usage(jc, t);
        }

        // Replace System.out with a file
        if (!StringUtils.isNullOrEmpty(params.dbPerformanceFile)) {
            out = new PrintStream(params.dbPerformanceFile);
            System.setErr(out);
        }

        deleteRecursively(baseFolder);
        new File(baseFolder, "/sqlite").mkdirs();

        // Start the HSQL, H2, and Derby servers in-process
        org.hsqldb.Server hsql = startHSQL();
        org.h2.tools.Server h2 = startH2();
        NetworkServerControl derby = startDerby();

        // Statement logging
        final FileWriter statementWriter;
        if (StringUtils.isNullOrEmpty(params.sqlStatementsFile)) {
            statementWriter = null;
        } else {
            statementWriter = new FileWriter(params.sqlStatementsFile);
        }
        IciqlListener statementListener = new IciqlListener() {
            @Override
            public void logIciql(StatementType type, String statement) {
                if (statementWriter == null) {
                    return;
                }
                try {
                    statementWriter.append(statement);
                    statementWriter.append('\n');
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        IciqlLogger.registerListener(statementListener);

        SuiteClasses suiteClasses = IciqlSuite.class.getAnnotation(SuiteClasses.class);
        long quickestDatabase = Long.MAX_VALUE;
        String dividerMajor = buildDivider('*', 79);
        String dividerMinor = buildDivider('-', 79);

        // Header
        out.println(dividerMajor);
        out.println(MessageFormat.format("{0} {1} ({2}) testing {3} database configurations", Constants.NAME,
                Constants.getVersion(), Constants.getBuildDate(), TEST_DBS.length));
        out.println(dividerMajor);
        out.println();

        showProperty("java.vendor");
        showProperty("java.runtime.version");
        showProperty("java.vm.name");
        showProperty("os.name");
        showProperty("os.version");
        showProperty("os.arch");
        showProperty("available processors", "" + Runtime.getRuntime().availableProcessors());
        showProperty(
                "available memory",
                MessageFormat.format("{0,number,0.0} GB", ((double) Runtime.getRuntime().maxMemory())
                        / (1024 * 1024)));
        out.println();

        // Test a database
        long lastCount = 0;
        for (TestDb testDb : TEST_DBS) {
            out.println(dividerMinor);
            out.println("Testing " + testDb.describeDatabase());
            out.println("        " + testDb.url);
            out.println(dividerMinor);

            // inject a database section delimiter in the statement log
            if (statementWriter != null) {
                statementWriter.append("\n\n");
                statementWriter.append("# ").append(dividerMinor).append('\n');
                statementWriter.append("# ").append("Testing " + testDb.describeDatabase()).append('\n');
                statementWriter.append("# ").append(dividerMinor).append('\n');
                statementWriter.append("\n\n");
            }

            if (testDb.getVersion().equals("OFFLINE")) {
                // Database not available
                out.println("Skipping.  Could not find " + testDb.url);
                out.println();
            } else {
                // Setup system properties
                System.setProperty("iciql.url", testDb.url);
                System.setProperty("iciql.user", testDb.username);
                System.setProperty("iciql.password", testDb.password);

                // Test database
                Result result = JUnitCore.runClasses(suiteClasses.value());

                // Report results
                testDb.runtime = result.getRunTime();
                if (testDb.runtime < quickestDatabase) {
                    quickestDatabase = testDb.runtime;
                }
                testDb.statements = IciqlLogger.getTotalCount() - lastCount;
                // reset total count for next database
                lastCount = IciqlLogger.getTotalCount();

                out.println(MessageFormat.format(
                        "{0} tests ({1} failures, {2} ignores)  {3} statements in {4,number,0.000} secs",
                        result.getRunCount(), result.getFailureCount(), result.getIgnoreCount(),
                        testDb.statements, result.getRunTime() / 1000f));

                if (result.getFailureCount() == 0) {
                    out.println();
                    out.println("  100% successful test suite run.");
                    out.println();
                } else {
                    for (Failure failure : result.getFailures()) {
                        out.println(MessageFormat.format("\n  + {0}\n    {1}", failure.getTestHeader(),
                                failure.getMessage()));
                    }
                    out.println();
                }
            }
        }

        // Display runtime results sorted by performance leader
        out.println();
        out.println(dividerMajor);
        out.println(MessageFormat.format("{0} {1} ({2}) test suite performance results", Constants.NAME,
                Constants.getVersion(), Constants.getBuildDate()));

        StringBuilder compressedSystem = new StringBuilder();
        compressedSystem.append("      on ");
        compressedSystem.append(System.getProperty("java.vendor"));
        compressedSystem.append(' ');
        compressedSystem.append(System.getProperty("java.runtime.version"));
        compressedSystem.append(", ");
        compressedSystem.append(System.getProperty("os.name"));
        compressedSystem.append(' ');
        compressedSystem.append(System.getProperty("os.version"));
        compressedSystem.append(", ");
        compressedSystem.append(System.getProperty("os.arch"));
        out.println(compressedSystem.toString());

        out.println(dividerMajor);
        List<TestDb> dbs = Arrays.asList(TEST_DBS);
        Collections.sort(dbs);

        out.println(MessageFormat.format("{0} {1} {2} {3} {4}",
                StringUtils.pad("Name", 11, " ", true),
                StringUtils.pad("Config", 16, " ", true),
                StringUtils.pad("Version", 25, " ", true),
                StringUtils.pad("Stats/sec", 10, " ", true),
                "Runtime"));
        out.println(dividerMinor);
        for (TestDb testDb : dbs) {
            DecimalFormat df = new DecimalFormat("0.0");
            out.println(MessageFormat.format("{0} {1} {2} {3} {4}s  ({5,number,0.0}x)",
                    StringUtils.pad(testDb.name, 11, " ", true),
                    StringUtils.pad(testDb.config, 16, " ", true),
                    StringUtils.pad(testDb.getVersion(), 23, " ", true),
                    StringUtils.pad("" + testDb.getStatementRate(), 7, " ", false),
                    StringUtils.pad(df.format(testDb.getRuntime()), 8, " ", false),
                    ((double) testDb.runtime) / quickestDatabase));
        }
        out.println(dividerMinor);

        // cleanup
        for (PoolableConnectionFactory factory : connectionFactories.values()) {
            factory.getPool().close();
        }
        IciqlLogger.unregisterListener(statementListener);
        out.close();
        System.setErr(ERR);
        if (statementWriter != null) {
            statementWriter.close();
        }
        hsql.stop();
        h2.stop();
        derby.shutdown();
        System.exit(0);
    }

    private static void showProperty(String name) {
        showProperty(name, System.getProperty(name));
    }

    private static void showProperty(String name, String value) {
        out.print(' ');
        out.print(StringUtils.pad(name, 25, " ", true));
        out.println(value);
    }

    private static void usage(JCommander jc, ParameterException t) {
        System.out.println(Constants.NAME + " test suite v" + Constants.getVersion());
        System.out.println();
        if (t != null) {
            System.out.println(t.getMessage());
            System.out.println();
        }
        if (jc != null) {
            jc.usage();
        }
        System.exit(0);
    }

    private static String buildDivider(char c, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static void deleteRecursively(File f) {
        if (f.isDirectory()) {
            for (File file : f.listFiles()) {
                if (file.isDirectory()) {
                    deleteRecursively(file);
                }
                file.delete();
            }
        }
        f.delete();
    }

    /**
     * Start an HSQL tcp server.
     *
     * @return an HSQL server instance
     * @throws Exception
     */
    private static org.hsqldb.Server startHSQL() throws Exception {
        HsqlProperties p = new HsqlProperties();
        String db = new File(System.getProperty("user.dir")).getAbsolutePath() + "/testdbs/hsqltcp/iciql";
        p.setProperty("server.database.0", "file:" + db);
        p.setProperty("server.dbname.0", "iciql");
        // set up the rest of properties

        // alternative to the above is
        org.hsqldb.Server server = new org.hsqldb.Server();
        server.setProperties(p);
        server.setLogWriter(null);
        server.setErrWriter(null);
        server.start();
        return server;
    }

    /**
     * Start the H2 tcp server.
     *
     * @return an H2 server instance
     * @throws Exception
     */
    private static org.h2.tools.Server startH2() throws Exception {
        org.h2.tools.Server server = org.h2.tools.Server.createTcpServer();
        server.start();
        return server;
    }

    /**
     * Start the Derby tcp server.
     *
     * @return an Derby server instance
     * @throws Exception
     */
    private static NetworkServerControl startDerby() throws Exception {
        NetworkServerControl serverControl = new NetworkServerControl();
        serverControl.start(null);
        return serverControl;
    }

    /**
     * Represents a test database url.
     */
    private static class TestDb implements Comparable<TestDb> {
        final String name;
        final String config;
        final String url;
        final String username;
        final String password;
        String version;
        long runtime;
        long statements;

        TestDb(String name, String config, String url) {
            this(name, config, url, "sa", "");
        }

        TestDb(String name, String config, String url, String username, String password) {
            this.name = name;
            this.config = config;
            this.url = url;
            this.username = username;
            this.password = password;
        }

        double getRuntime() {
            return runtime / 1000d;
        }

        int getStatementRate() {
            return Double.valueOf((statements) / (runtime / 1000d)).intValue();
        }

        String describeDatabase() {
            StringBuilder sb = new StringBuilder(name);
            sb.append(" ");
            sb.append(getVersion());
            return sb.toString();
        }

        String getVersion() {
            if (version == null) {
                try {
                    Db db = Db.open(url, username, password);
                    version = db.getConnection().getMetaData().getDatabaseProductVersion();
                    db.close();
                    return version;
                } catch (Throwable t) {
                    version = "OFFLINE";
                }
            }
            return version;
        }

        @Override
        public int compareTo(TestDb o) {
            if (runtime == 0) {
                return 1;
            }
            if (o.runtime == 0) {
                return -1;
            }
            int r1 = getStatementRate();
            int r2 = o.getStatementRate();
            if (r1 == r2) {
                return 0;
            }
            if (r1 < r2) {
                return 1;
            }
            return -1;
        }
    }

    /**
     * Command-line parameters for TestSuite.
     */
    @Parameters(separators = " ")
    private static class Params {

        @Parameter(names = {"--dbFile"}, description = "Database performance results text file", required = false)
        public String dbPerformanceFile;

        @Parameter(names = {"--sqlFile"}, description = "SQL statements log file", required = false)
        public String sqlStatementsFile;
    }
}