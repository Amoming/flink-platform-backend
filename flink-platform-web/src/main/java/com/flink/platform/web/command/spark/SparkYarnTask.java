package com.flink.platform.web.command.spark;

import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.web.command.shell.ShellTask;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.external.YarnClientService;
import com.flink.platform.web.util.CollectLogRunnable;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.function.BiConsumer;
import java.util.regex.Matcher;

import static com.flink.platform.common.constants.JobConstant.SPARK_APP_ID_PATTERN;
import static com.flink.platform.common.enums.DeployMode.FLINK_YARN_PER;
import static com.flink.platform.web.util.CollectLogRunnable.CmdOutType.STD;

@Slf4j
@Getter
@Setter
public class SparkYarnTask extends ShellTask {
    private YarnClientService yarnClientService;

    private DeployMode mode;

    private String appId;


    public SparkYarnTask(long jobRunId, DeployMode mode, String command, String[] envs, long timeoutMills) {
        super(jobRunId, command, envs, timeoutMills, JobType.SPARK);
        this.mode = mode;
        setLogConsumer(this.extractAppIdAndJobId());
    }

    /** Only for kill job. */
    public SparkYarnTask(long jobRunId, DeployMode mode) {
        super(jobRunId, null, null, 0, JobType.SPARK);
        this.mode = mode;
        this.yarnClientService = SpringContext.getBean(YarnClientService.class);
    }

    public void run() throws Exception {
        super.run();
    }

    @Override
    public void cancel() {
        // kill shell.
        super.cancel();

        // kill application.
        if (StringUtils.isNotEmpty(appId)) {
            if (FLINK_YARN_PER.equals(mode)) {
                try {
                    yarnClientService.killApplication(appId);
                } catch (Exception e) {
                    log.error("Kill yarn application: {} failed", appId, e);
                }
            } else {
                log.warn("Kill command unsupported deployMode: {}, applicationId: {}", mode, appId);
            }
        }
    }

    public BiConsumer<CollectLogRunnable.CmdOutType, String> extractAppIdAndJobId() {
        System.out.println("set log consumer spark");
        return (cmdOutType, line) -> {
            if (cmdOutType != STD) {
                return;
            }
            if (StringUtils.isEmpty(appId)) {
                String id = extractApplicationId(line); // 如果还没有appId，就提取line进行正则匹配得到appId

                if (StringUtils.isNotEmpty(id)) {
                    appId = id;
                }
            }

        };
    }

    public static String extractApplicationId(String message) {
        Matcher matcher = SPARK_APP_ID_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }
}
