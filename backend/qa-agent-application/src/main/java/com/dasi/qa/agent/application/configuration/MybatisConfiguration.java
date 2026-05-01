package com.dasi.qa.agent.application.configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.dasi.qa.agent.application.properties.MybatisProperties;
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
@EnableConfigurationProperties(MybatisProperties.class)
@MapperScans({
    @MapperScan(
        basePackages = "com.dasi.qa.agent.infrastructure.persistent.mapper.mysql",
        sqlSessionFactoryRef = "mysqlSqlSessionFactory"
    ),
    @MapperScan(
        basePackages = "com.dasi.qa.agent.infrastructure.persistent.mapper.postgres",
        sqlSessionFactoryRef = "postgresSqlSessionFactory"
    )
})
public class MyBatisConfiguration {

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
    public SqlSessionFactory mysqlSqlSessionFactory(
        @Qualifier("mysqlDataSource") DataSource dataSource,
        MybatisProperties mybatisProperties,
        MybatisPlusInterceptor interceptor
    ) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(resolveMapperLocations(mybatisProperties.getMysqlMapperLocations()));
        MybatisConfiguration mybatisConfiguration = new MybatisConfiguration();
        mybatisConfiguration.setMapUnderscoreToCamelCase(mybatisProperties.isMapUnderscoreToCamelCase());
        mybatisConfiguration.setCacheEnabled(mybatisProperties.isCacheEnabled());
        factoryBean.setConfiguration(mybatisConfiguration);
        factoryBean.setPlugins(interceptor);
        return factoryBean.getObject();
    }

    @Bean(name = "postgresSqlSessionFactory")
    public SqlSessionFactory postgresSqlSessionFactory(
        @Qualifier("postgresDataSource") DataSource dataSource,
        MybatisProperties mybatisProperties
    ) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(resolveMapperLocations(mybatisProperties.getPostgresMapperLocations()));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(mybatisProperties.isMapUnderscoreToCamelCase());
        configuration.setCacheEnabled(mybatisProperties.isCacheEnabled());
        factoryBean.setConfiguration(configuration);
        return factoryBean.getObject();
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

    private Resource[] resolveMapperLocations(String mapperLocations) throws Exception {
        if (!StringUtils.hasText(mapperLocations)) {
            return new Resource[0];
        }
        return new PathMatchingResourcePatternResolver().getResources(mapperLocations);
    }
}
