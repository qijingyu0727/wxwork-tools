package com.config;
import javax.sql.DataSource;

import jakarta.annotation.PostConstruct;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;


@Configuration
public class FlywayConfig {

    @Autowired
    private DataSource dataSource;

    private Logger logger = LoggerFactory.getLogger(this.getClass());


    @PostConstruct
    public void migrate() {


        FluentConfiguration wxworkArchiveSchemaVersion = Flyway.configure().dataSource(dataSource)
                .locations("db-migration")
                .encoding("UTF-8")
                .table("wxwork_archive_schema_version")
                .baselineOnMigrate(true)
                .outOfOrder(true);

        Flyway flyway = new Flyway(wxworkArchiveSchemaVersion);

        try {
            flyway.migrate();
        } catch (FlywayException e) {
            flyway.repair();
            logger.error("Flyway配置加载出错",e);
        }

    }
}