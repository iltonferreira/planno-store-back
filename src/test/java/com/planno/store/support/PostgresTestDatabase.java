package com.planno.store.support;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;

/** Native PostgreSQL process, fresh private cluster, loopback only; no external URL is accepted. */
public final class PostgresTestDatabase {
    private static final String OWNER = "planno_test_owner";
    private static final String APP = "planno_test_app";
    private static final String H2_URL = "jdbc:h2:mem:planno_store_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
    private static final Set<String> OWNED_DATABASES = ConcurrentHashMap.newKeySet();

    private PostgresTestDatabase() { }

    private static final class Holder {
        private static final Cluster CLUSTER = start();
    }

    private record Cluster(Path root, Path bin, int port, String password, String database) {
        String url(String name) { return "jdbc:postgresql://127.0.0.1:" + port + "/" + name; }
    }

    public static Map<String, Object> properties() {
        Cluster cluster = cluster();
        Map<String, Object> settings = new HashMap<>();
        settings.put("spring.datasource.url", cluster.url(cluster.database()));
        settings.put("spring.datasource.username", APP);
        settings.put("spring.datasource.password", cluster.password());
        settings.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        settings.put("spring.datasource.hikari.maximum-pool-size", 8);
        settings.put("spring.datasource.hikari.minimum-idle", 2);
        settings.put("spring.datasource.hikari.connection-timeout", 10000);
        settings.put("spring.jpa.properties.hibernate.default_schema", "public");
        settings.put("spring.flyway.schemas", "public");
        settings.put("spring.flyway.default-schema", "public");
        // Flyway can use a separate datasource: pin it before any migration runs.
        settings.put("spring.flyway.url", cluster.url(cluster.database()));
        settings.put("spring.flyway.user", APP);
        settings.put("spring.flyway.password", cluster.password());
        settings.put("spring.flyway.driver-class-name", "org.postgresql.Driver");
        settings.put("spring.flyway.locations", "classpath:db/migration");
        settings.put("spring.flyway.clean-disabled", true);
        settings.put("spring.flyway.enabled", true);
        settings.put("spring.sql.init.mode", "never");
        settings.put("spring.jpa.hibernate.ddl-auto", "validate");
        settings.put("app.payment.mercado-pago.mock-enabled", true);
        settings.put("app.media.supabase.mock-enabled", true);
        settings.put("app.bootstrap.store.enabled", false);
        settings.put("app.bootstrap.admin.enabled", false);
        settings.put("app.order-reservation.enabled", false);
        return settings;
    }

    public static Connection connect() throws SQLException { return connect(cluster().database()); }

    public static Connection connect(String database) throws SQLException {
        Cluster cluster = cluster();
        if (!OWNED_DATABASES.contains(database)) {
            throw new IllegalArgumentException("Only databases created by this test JVM may be accessed");
        }
        return connection(cluster, database, APP);
    }

    public static synchronized String createDatabase() {
        Cluster cluster = cluster();
        String database = "planno_test_" + UUID.randomUUID().toString().replace("-", "");
        createDatabase(cluster, database);
        return database;
    }

    /** Check configuration before acquiring a connection or deleting any fixture. */
    public static void assertSafeCleanup(JdbcTemplate jdbc) {
        if (!(jdbc.getDataSource() instanceof HikariDataSource dataSource)) {
            throw new IllegalStateException("Unrecognized test datasource; cleanup refused");
        }
        String expected = Boolean.getBoolean("planno.test.postgres")
                ? cluster().url(cluster().database()) : H2_URL;
        if (!expected.equals(dataSource.getJdbcUrl())) {
            throw new IllegalStateException("Cleanup is restricted to the owned disposable test datasource");
        }
        if (Boolean.getBoolean("planno.test.postgres")) {
            String database = jdbc.queryForObject("select current_database()", String.class);
            if (!cluster().database().equals(database)) {
                throw new IllegalStateException("Wrong database; cleanup refused");
            }
        }
    }

    private static Cluster cluster() {
        if (!Boolean.getBoolean("planno.test.postgres")) {
            throw new IllegalStateException("Real PostgreSQL tests require -Ppostgres-integration");
        }
        return Holder.CLUSTER;
    }

    private static Cluster start() {
        String configured = System.getProperty("planno.test.postgres.bin-dir", "");
        Path bin = Path.of(configured).toAbsolutePath().normalize();
        for (String executable : Set.of("postgres", "initdb", "pg_ctl")) {
            if (configured.isBlank() || !Files.isExecutable(bin.resolve(executable))) {
                throw new IllegalStateException("Provide a PostgreSQL binary directory; there is no H2 fallback");
            }
        }
        Cluster cluster = null;
        try {
            Path root = Files.createTempDirectory(Path.of("/private/tmp"), "planno-pg-");
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
            Files.writeString(root.resolve("owned-by-planno-test"), UUID.randomUUID().toString());
            Path socket = Files.createDirectory(root.resolve("socket"));
            String password = UUID.randomUUID().toString().replace("-", "");
            String database = "planno_test_" + UUID.randomUUID().toString().replace("-", "");
            cluster = new Cluster(root, bin, 0, password, database);
            int port;
            try (ServerSocket available = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
                port = available.getLocalPort();
            }
            cluster = new Cluster(root, bin, port, password, database);
            Path passwordFile = root.resolve("init-password");
            Files.writeString(passwordFile, password);
            Files.setPosixFilePermissions(passwordFile, PosixFilePermissions.fromString("rw-------"));
            run(cluster, "initdb", "-D", root.resolve("data").toString(), "--username=" + OWNER,
                    "--pwfile=" + passwordFile, "--auth-host=scram-sha-256", "--auth-local=scram-sha-256",
                    "--encoding=UTF8", "--locale=C");
            Files.delete(passwordFile);
            run(cluster, "pg_ctl", "-D", root.resolve("data").toString(), "-l", root.resolve("server.log").toString(),
                    "-o", "-h 127.0.0.1 -p " + port + " -k " + socket + " -c max_connections=100 -c log_statement=none",
                    "-w", "start");
            try (Connection admin = connection(cluster, "postgres", OWNER); var statement = admin.createStatement()) {
                statement.execute("CREATE ROLE " + APP + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '" + password + "'");
            }
            createDatabase(cluster, cluster.database());
            Cluster owned = cluster;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> stop(owned), "planno-postgres-cleanup"));
            try (Connection connection = connectOwned(cluster, cluster.database()); var statement = connection.createStatement();
                    var result = statement.executeQuery("select version()")) {
                result.next();
                Path artifacts = Files.createDirectories(Path.of("target", "postgres-test-artifacts"));
                Files.writeString(artifacts.resolve("environment.txt"), result.getString(1)
                        + "\nlisten_addresses=127.0.0.1\nport=" + port + "\napplication_role=" + APP
                        + " (NOSUPERUSER, NOCREATEDB, NOCREATEROLE)\ncluster=" + root + "\n");
                System.out.println("Disposable PostgreSQL started: " + result.getString(1));
            }
            return cluster;
        } catch (Exception exception) {
            if (cluster != null) { stop(cluster); }
            throw new IllegalStateException("Could not start disposable PostgreSQL; no fallback", exception);
        }
    }

    private static void createDatabase(Cluster cluster, String name) {
        try (Connection admin = connection(cluster, "postgres", OWNER); var statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + name + " OWNER " + APP);
            OWNED_DATABASES.add(name);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create disposable test database", exception);
        }
    }

    private static Connection connectOwned(Cluster cluster, String database) throws SQLException {
        return connection(cluster, database, APP);
    }

    private static Connection connection(Cluster cluster, String database, String user) throws SQLException {
        Properties settings = new Properties();
        settings.setProperty("user", user);
        settings.setProperty("password", cluster.password());
        settings.setProperty("connectTimeout", "5");
        return DriverManager.getConnection(cluster.url(database), settings);
    }

    private static void run(Cluster cluster, String executable, String... args) throws IOException, InterruptedException {
        var command = new java.util.ArrayList<String>();
        command.add(cluster.bin().resolve(executable).toString());
        command.addAll(java.util.List.of(args));
        var builder = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(cluster.root().resolve("bootstrap.log").toFile()));
        builder.environment().keySet().removeIf(key -> key.startsWith("PG"));
        Process process = builder.start();
        if (!process.waitFor(45, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Disposable PostgreSQL command timed out: " + executable);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Disposable PostgreSQL command failed: " + executable
                    + "; diagnostics: " + cluster.root().resolve("bootstrap.log"));
        }
    }

    private static void stop(Cluster cluster) {
        try {
            if (!Files.exists(cluster.root().resolve("owned-by-planno-test"))) { return; }
            boolean running = Files.exists(cluster.root().resolve("data/postmaster.pid"));
            if (running) { run(cluster, "pg_ctl", "-D", cluster.root().resolve("data").toString(), "-m", "fast", "-w", "stop"); }
            Path artifacts = Files.createDirectories(Path.of("target", "postgres-test-artifacts"));
            for (String name : java.util.List.of("server.log", "bootstrap.log")) {
                Path source = cluster.root().resolve(name);
                if (Files.exists(source)) {
                    Files.copy(source, artifacts.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            // Never follow links or delete anything except this JVM's newly created cluster.
            try (var paths = Files.walk(cluster.root())) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
            }
            Files.writeString(artifacts.resolve("cleanup.txt"), "Owned PostgreSQL stopped; temporary cluster deleted.\n");
        } catch (Exception exception) {
            System.err.println("Disposable PostgreSQL cleanup incomplete; retained cluster: " + cluster.root());
        }
    }
}
