package com.dasi.qa.agent.application.configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.dasi.qa.agent.application.properties.MybatisProperties;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@Configuration
@MapperScans({
    @MapperScan(
        basePackages = "com.dasi.qa.agent.infrastructure.persistent.mysql.mapper",
        sqlSessionFactoryRef = "mysqlSqlSessionFactory"
    ),
    @MapperScan(
        basePackages = "com.dasi.qa.agent.infrastructure.persistent.postgres.mapper",
        sqlSessionFactoryRef = "postgresSqlSessionFactory"
    )
})
@EnableConfigurationProperties({
    MybatisProperties.class
})
public class MybatisConfiguration {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(MybatisProperties properties) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInnerInterceptor.setOverflow(properties.isOverflow());
        paginationInnerInterceptor.setMaxLimit(properties.getMaxLimit());
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        return interceptor;
    }

    @Bean(name = "mysqlSqlSessionFactory")
    @Primary
    public SqlSessionFactory mysqlSqlSessionFactory(
        @Qualifier("mysqlDataSource") DataSource dataSource,
        MybatisProperties mybatisProperties,
        MybatisPlusInterceptor interceptor
    ) throws Exception {
        return buildSqlSessionFactory(
            dataSource,
            mybatisProperties.getMysqlMapperLocations(),
            mybatisProperties,
            interceptor
        );
    }

    @Bean(name = "postgresSqlSessionFactory")
    public SqlSessionFactory postgresSqlSessionFactory(
        @Qualifier("postgresDataSource") DataSource dataSource,
        MybatisProperties mybatisProperties
    ) throws Exception {
        return buildPostgresSqlSessionFactory(
            dataSource,
            mybatisProperties.getPostgresMapperLocations(),
            mybatisProperties
        );
    }

    @Bean(name = "mysqlSqlSessionTemplate")
    @Primary
    public SqlSessionTemplate mysqlSqlSessionTemplate(
        @Qualifier("mysqlSqlSessionFactory") SqlSessionFactory sqlSessionFactory
    ) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "postgresSqlSessionTemplate")
    public SqlSessionTemplate postgresSqlSessionTemplate(
        @Qualifier("postgresSqlSessionFactory") SqlSessionFactory sqlSessionFactory
    ) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    private SqlSessionFactory buildSqlSessionFactory(
        DataSource dataSource,
        String mapperLocations,
        MybatisProperties properties,
        Interceptor... interceptors
    ) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(resolveMapperLocations(mapperLocations));
        if (StringUtils.hasText(properties.getTypeAliasesPackage())) {
            factoryBean.setTypeAliasesPackage(properties.getTypeAliasesPackage());
        }
        if (StringUtils.hasText(properties.getTypeHandlersPackage())) {
            factoryBean.setTypeHandlersPackage(properties.getTypeHandlersPackage());
        }
        com.baomidou.mybatisplus.core.MybatisConfiguration mybatisConfiguration =
            new com.baomidou.mybatisplus.core.MybatisConfiguration();
        mybatisConfiguration.setMapUnderscoreToCamelCase(properties.isMapUnderscoreToCamelCase());
        mybatisConfiguration.setCacheEnabled(properties.isCacheEnabled());
        factoryBean.setConfiguration(mybatisConfiguration);
        if (interceptors != null && interceptors.length > 0) {
            factoryBean.setPlugins(interceptors);
        }
        return factoryBean.getObject();
    }

    private SqlSessionFactory buildPostgresSqlSessionFactory(
        DataSource dataSource,
        String mapperLocations,
        MybatisProperties properties
    ) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(resolveMapperLocations(mapperLocations));
        if (StringUtils.hasText(properties.getTypeAliasesPackage())) {
            factoryBean.setTypeAliasesPackage(properties.getTypeAliasesPackage());
        }
        if (StringUtils.hasText(properties.getTypeHandlersPackage())) {
            factoryBean.setTypeHandlersPackage(properties.getTypeHandlersPackage());
        }
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(properties.isMapUnderscoreToCamelCase());
        configuration.setCacheEnabled(properties.isCacheEnabled());
        factoryBean.setConfiguration(configuration);
        return factoryBean.getObject();
    }

    private Resource[] resolveMapperLocations(String mapperLocations) throws Exception {
        if (!StringUtils.hasText(mapperLocations)) {
            return new Resource[0];
        }
        return new PathMatchingResourcePatternResolver().getResources(mapperLocations);
    }
}
