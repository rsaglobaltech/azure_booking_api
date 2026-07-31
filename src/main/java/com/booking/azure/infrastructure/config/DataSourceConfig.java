package com.booking.azure.infrastructure.config;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url:jdbc:oracle:thin:@//localhost:1521/XEPDB1}")
    private String url;

    @Value("${spring.datasource.username:system}")
    private String username;

    @Value("${spring.datasource.password:oracle}")
    private String password;

    @Bean
    public DataSource dataSource() throws SQLException {
        PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
        pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        pds.setURL(url);
        pds.setUser(username);
        pds.setPassword(password);
        
        pds.setInitialPoolSize(5);
        pds.setMinPoolSize(5);
        pds.setMaxPoolSize(20);
        
        return pds;
    }
}


