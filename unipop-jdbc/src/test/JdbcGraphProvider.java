package test;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.LoadGraphWith;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.unipop.test.UnipopGraphProvider;

import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

/**
 * @author Gur Ronen
 * @since 6/20/2016
 */
public class JdbcGraphProvider extends UnipopGraphProvider {
    private final Connection jdbcConnection;

    public JdbcGraphProvider() throws SQLException, ClassNotFoundException {
        Class.forName("org.h2.Driver");
        this.jdbcConnection = DriverManager.getConnection("jdbc:h2:mem:gremlin;");

        createTables();
    }

    @Override
    public Map<String, Object> getBaseConfiguration(String graphName, Class<?> test, String testMethodName, LoadGraphWith.GraphData loadGraphWith) {
        Map<String, Object> baseConfiguration = super.getBaseConfiguration(graphName, test, testMethodName, loadGraphWith);
        String configurationFile = getSchemaConfiguration(loadGraphWith);
        URL url = this.getClass().getResource(configurationFile);
        String file = url.getFile();
        if (System.getProperty("os.name").toLowerCase().contains("windows"))
            file = file.substring(1);
        baseConfiguration.put("providers", file);
        return baseConfiguration;
    }

    private String getSchemaConfiguration(LoadGraphWith.GraphData loadGraphWith) {
        String confDirectory = "/configuration/" + System.getenv("conf") + "/";
        if (loadGraphWith != null)
            switch (loadGraphWith) {
                case MODERN:
                    return confDirectory + "modern";
                case GRATEFUL:
                    return confDirectory + "grateful";
                default:
                    return "/configuration/basic/default";
            }
        return "/configuration/basic/default";
    }

    @Override
    public void clear(Graph graph, Configuration configuration) throws Exception {
        super.clear(graph, configuration);
        truncateTables();
    }

    private void createTables() throws SQLException {
        //region dull tables
        this.jdbcConnection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS VERTEX_INNER(" +
                        "ID VARCHAR (100) NOT NULL," +
                        "LABEL VARCHAR(100) NOT NULL," +
                        "NAME VARCHAR(100)," +
                        "marko VARCHAR(100)," +
                        "josh VARCHAR(100)," +
                        "vadas VARCHAR(100)," +
                        "ripple VARCHAR(100)," +
                        "lop VARCHAR(100)," +
                        "peter VARCHAR(100)," +
                        "AGE INT," +
                        "LANG VARCHAR (100)," +
                        "KNOWNBY VARCHAR (100)," +
                        "CREATEDBY VARCHAR (100)," +
                        "EDGEID VARCHAR (100)," +
                        "EDGELANG VARCHAR (100)," +
                        "EDGEWEIGHT DOUBLE," +
                        "EDGENAME VARCHAR(100))"
        );

        this.jdbcConnection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS vertices(" +
                        "ID VARCHAR(100) NOT NULL PRIMARY KEY, " +
                        "LABEL VARCHAR(100) NOT NULL," +
                        "name varchar(100), " +
                        "age int, " +
                        "location VARCHAR(100)," +
                        "status VARCHAR(100)," +
                        "oid int," +
                        "test VARCHAR(100)," +
                        "communityIndex int," +
                        "data VARCHAR(100)," +
                        "lang VARCHAR(100))");

        this.jdbcConnection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS edges(" +
                        "ID VARCHAR(100) NOT NULL PRIMARY KEY, " +
                        "LABEL VARCHAR(100) NOT NULL, " +
                        "outId VARCHAR(100), " +
                        "outLabel VARCHAR(100), " +
                        "inId VARCHAR(100), " +
                        "inLabel VARCHAR(100)," +
                        "year VARCHAR(100)," +
                        "acl VARCHAR(100)," +
                        "time VARCHAR(100)," +
                        "location VARCHAR(100)," +
                        "data VARCHAR(100)," +
                        "weight DOUBLE)");
        //endregion

        //region modern tables
        this.jdbcConnection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS PERSON_MODERN(" +
                        "id VARCHAR(100) NOT NULL, " +
                        "name varchar(100), " +
                        "age int)");

        this.jdbcConnection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS ANIMAL_MODERN(" +
                        "id VARCHAR(100) NOT NULL, " +
                        "name varchar(100), " +
                        "age int)");

        this.jdbcConnection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS SOFTWARE_MODERN(" +
                        "id VARCHAR(100) NOT NULL, " +
                        "name varchar(100), " +
                        "lang VARCHAR(100))");

        this.jdbcConnection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS MODERN_EDGES(" +
                        "id VARCHAR(100) NOT NULL, " +
                        "label VARCHAR(100) NOT NULL, " +
                        "outId VARCHAR(100), " +
                        "outLabel VARCHAR(100), " +
                        "inId VARCHAR(100), " +
                        "inLabel VARCHAR(100)," +
                        "year int," +
                        "weight DOUBLE)");
        //endregion

        //region crew tables
        this.jdbcConnection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS PERSON_CREW(" +
                        "id VARCHAR(100) NOT NULL, " +
                        "name VARCHAR(100)," +
                        "location VARCHAR(100)," +
                        "lang VARCHAR(100))");

        this.jdbcConnection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS SOFTWARE_CREW(" +
                        "id VARCHAR(100) NOT NULL, " +
                        "name VARCHAR(100))");

        this.jdbcConnection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS DEVELOPS_CREW(" +
                        "id VARCHAR(100) NOT NULL, " +
                        "outId VARCHAR(100), " +
                        "outLabel VARCHAR(100), " +
                        "inId VARCHAR(100), " +
                        "inLabel VARCHAR(100)," +
                        "since int)");

        this.jdbcConnection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS USES_CREW(" +
                        "id VARCHAR(100) NOT NULL, " +
                        "outId VARCHAR(100), " +
                        "outLabel VARCHAR(100), " +
                        "inId VARCHAR(100), " +
                        "inLabel VARCHAR(100)," +
                        "skill int)");
        //endregion

        //region grateful dead
        this.jdbcConnection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS ARTIST_GRATEFUL_DEAD(" +
                        "id VARCHAR(100) NOT NULL, " +
                        "name VARCHAR(100))"
        );

        this.jdbcConnection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS SONG_GRATEFUL_DEAD(" +
                        "id VARCHAR(100) NOT NULL, " +
                        "name VARCHAR(100)," +
                        "songType VARCHAR(100)," +
                        "performances int)"
        );

        this.jdbcConnection.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS GRATEFUL_DEAD_EDGES(" +
                        "id VARCHAR(100) NOT NULL PRIMARY KEY, " +
                        "label VARCHAR(100) NOT NULL," +
                        "outId VARCHAR(100), " +
                        "outLabel VARCHAR(100), " +
                        "inId VARCHAR(100), " +
                        "inLabel VARCHAR(100)," +
                        "weight DOUBLE)"
        );
        //endregion
    }

    public void truncateTables() throws SQLException {
        this.jdbcConnection.createStatement().execute("TRUNCATE TABLE vertices");
        this.jdbcConnection.createStatement().execute("TRUNCATE TABLE edges");
        this.jdbcConnection.createStatement().execute("TRUNCATE TABLE vertex_inner");

        this.jdbcConnection.createStatement().execute("TRUNCATE TABLE PERSON_MODERN");
        this.jdbcConnection.createStatement().execute("TRUNCATE TABLE ANIMAL_MODERN");
        this.jdbcConnection.createStatement().execute("TRUNCATE TABLE SOFTWARE_MODERN");

        this.jdbcConnection.createStatement().execute("TRUNCATE TABLE PERSON_CREW");
        this.jdbcConnection.createStatement().execute("TRUNCATE TABLE SOFTWARE_CREW");

        this.jdbcConnection.createStatement().execute("TRUNCATE TABLE ARTIST_GRATEFUL_DEAD");
        this.jdbcConnection.createStatement().execute("TRUNCATE TABLE SONG_GRATEFUL_DEAD");

        this.jdbcConnection.createStatement().execute("TRUNCATE TABLE MODERN_EDGES");
        this.jdbcConnection.createStatement().execute("TRUNCATE TABLE DEVELOPS_CREW");
        this.jdbcConnection.createStatement().execute("TRUNCATE TABLE USES_CREW");
        this.jdbcConnection.createStatement().execute("TRUNCATE TABLE GRATEFUL_DEAD_EDGES");
    }
}
