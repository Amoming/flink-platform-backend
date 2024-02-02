package com.flink.platform.web.command.spark;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.web.command.JobCommand;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;

@Setter
@Getter
public class SparkCommand extends JobCommand {
    private DeployMode mode;

    private String prefix;

    private String optionArgs;

    private final Map<String, Object> configs = new LinkedHashMap<>();

    private String mainArgs;

    private String mainClass;

    private String mainJar;

    private List<URL> classpaths;

    public SparkCommand(long jobRunId, DeployMode mode) {
        super(jobRunId);
        this.mode = mode;
    }

    @Override
    public String toCommandString() {
        StringBuilder command = new StringBuilder(prefix + LINE_SEPARATOR);
        if (StringUtils.isNotBlank(optionArgs)) {
            command.append(optionArgs).append(LINE_SEPARATOR);
        }
//        configs.forEach((k, v) -> command.append(String.format("-D%s=%s" + LINE_SEPARATOR, k, v)));
        classpaths.forEach(classpath -> command.append(String.format("--jars %s" + LINE_SEPARATOR, classpath)));
        command.append(String.format("--class %s" + LINE_SEPARATOR, mainClass))
                .append(String.format("%s" + LINE_SEPARATOR, mainJar))
                .append(String.format(" %s ", StringUtils.defaultString(mainArgs, "")));
        return command.toString();
    }

    /**
     * spark-submit --class com.szubd.rspalgos.App \
     *      --name GEO_DIS_SparkG_Susy_100Part_2St_2Pre_[RF,LR,GBT,SVM,LOGOADABOOST] \
     * 	 --conf spark.MLP.maxIter=10\
     *      --master yarn \
     *      --deploy-mode cluster \
     *      --driver-memory 16g \
     *      --executor-memory 16g \
     *      exe100-geologo.jar clf-simple sparkGeo "RF,LR,GBT,MLP,LOGOADABOOST" \
     *      "/user/zhangyuming/SUSY/Susy_Train.parquet,/user/zhangyuming/SUSY/Susy_Train.parquet,/user/zhangyuming/SUSY/Susy_Train.parquet,/user/zhangyuming/SUSY/Susy_Train.parquet,/user/zhangyuming/SUSY/Susy_Train.parquet" \
     *      0 5 75 "/user/zhangyuming/SUSY/Susy_Test.parquet" 1
     */
}
