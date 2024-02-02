package com.flink.platform.web.command.flink;

import com.flink.platform.web.config.FlinkConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Flink 1.12 command builder. */
@Slf4j
@Component("flink112CommandBuilder")
public class Flink112CommandBuilder extends FlinkCommandBuilder {

    /**
     * 不同的版本注入的配置不同
     * @param flinkConfig
     */
    @Autowired
    public Flink112CommandBuilder(@Qualifier("flink112") FlinkConfig flinkConfig) {
        super(flinkConfig);
    }
}
