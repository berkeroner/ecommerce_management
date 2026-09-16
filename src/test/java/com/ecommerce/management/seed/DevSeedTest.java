package com.ecommerce.management.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class DevSeedTest {
    @Test
    void defaultContextRunsSchemaWithoutSeedData() throws Exception {
        DataSource source = database();
        migrate(source, null);
        JdbcTemplate jdbc = new JdbcTemplate(source);
        for (String table : new String[] {"categories", "customers", "products", "addresses"}) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        }
    }

    @Test
    void devSeedPreservesExistingRecordsResolvesIdsAndDoesNotDuplicate() throws Exception {
        DataSource source = database();
        migrate(source, null);
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.update("""
                INSERT INTO categories (id, name, slug, is_active, created_at, updated_at)
                VALUES (100, 'Existing category', 'elektronik', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO customers (id, name, email, status, created_at, updated_at)
                VALUES (200, 'Existing customer', 'ayse.yilmaz@example.com', 'PASSIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        migrate(source, "dev");
        migrate(source, "dev");
        // Also verify SQL guards independently from Liquibase's once-only tracking.
        new ResourceDatabasePopulator(new ClassPathResource("db/changelog/seed/realistic-seed.sql"))
                .execute(source);

        assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM categories", Integer.class));
        assertEquals(24, jdbc.queryForObject("SELECT COUNT(*) FROM customers", Integer.class));
        assertEquals(48, jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class));
        assertEquals(30, jdbc.queryForObject("SELECT COUNT(*) FROM addresses", Integer.class));
        assertEquals("Existing category", jdbc.queryForObject("SELECT name FROM categories WHERE id = 100", String.class));
        assertEquals("PASSIVE", jdbc.queryForObject("SELECT status FROM customers WHERE id = 200", String.class));
        assertEquals(100L, jdbc.queryForObject("SELECT category_id FROM products WHERE sku = 'ELK-TEL-1001'", Long.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM addresses WHERE addressable_id = 200 AND addressable_type = 'CUSTOMER'", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE stock = 0", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE sku LIKE 'DEMO-%'", Integer.class));
    }

    private DataSource database() {
        return new DriverManagerDataSource("jdbc:h2:mem:seed-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private void migrate(DataSource source, String contexts) throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.setContexts(contexts);
        liquibase.afterPropertiesSet();
    }

}
