package com.flink.platform.dao.entity.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SparkJob extends BaseJob{
    /** option arguments. such as: -ynm jobName */
    private String optionArgs;

    /** configs for run job. */
    private Map<String, String> configs;

    /** external jars, such as udf jar. */
    private List<Long> extJars;

    @JsonIgnore
    private transient List<String> extJarPaths;

    /** main args. */
    private String mainArgs;

    /** main class. */
    private String mainClass;

    /** wait until the streaming job terminate. */
    private boolean waitForTermination;
}
