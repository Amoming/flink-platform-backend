package com.flink.platform.web.command.shell;

import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.result.ShellCallback;
import com.flink.platform.web.command.AbstractTask;
import com.flink.platform.web.util.CollectLogRunnable;
import com.flink.platform.web.util.CommandUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;

import java.io.InputStream;
import java.util.function.BiConsumer;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;
import static com.flink.platform.common.enums.ExecutionStatus.FAILURE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLABLE;
import static com.flink.platform.common.enums.ExecutionStatus.KILLED;
import static com.flink.platform.common.enums.ExecutionStatus.SUCCESS;
import static com.flink.platform.web.util.CollectLogRunnable.CmdOutType;
import static com.flink.platform.web.util.CollectLogRunnable.CmdOutType.ERR;
import static com.flink.platform.web.util.CollectLogRunnable.CmdOutType.STD;
import static com.flink.platform.web.util.CommandUtil.EXIT_CODE_FAILURE;
import static com.flink.platform.web.util.CommandUtil.EXIT_CODE_KILLED;
import static com.flink.platform.web.util.CommandUtil.EXIT_CODE_SUCCESS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/** Flink yarn task. */
@Slf4j
@Getter
@Setter
public class ShellTask extends AbstractTask {

    protected String command;

    protected String[] envs;

    protected long timeoutMills;

    protected BiConsumer<CmdOutType, String> logConsumer;

    protected Process process;

    protected Long processId;

    protected Long[] subprocessIds;

    protected boolean exited;

    protected int exitValue;

    protected final StringBuffer stdMsg = new StringBuffer();

    protected final StringBuffer errMsg = new StringBuffer();

    protected JobType jobType;

    public ShellTask(long id, String command, String[] envs, long timeoutMills) {
        super(id);
        this.command = command;
        this.envs = envs;
        this.timeoutMills = timeoutMills;
        this.logConsumer = newLogBuffer(null);
    }

    /** Only for kill command process. */
    public ShellTask(long id, Long processId) {
        super(id);
        this.processId = processId;
    }

    public ShellTask(long id, String command, String[] envs, long timeoutMills, JobType jobType) {
        super(id);
        this.command = command;
        this.envs = envs;
        this.timeoutMills = timeoutMills;
        this.logConsumer = newLogBuffer(null);
        this.jobType = jobType;
    }

    @Override
    public void run() throws Exception {
        log.info("Exec command: {}, env properties: {}", command, envs); // 打印command
        this.process = Runtime.getRuntime().exec(command, envs);
        this.processId = CommandUtil.getProcessId(process);
        try (InputStream stdStream = process.getInputStream();
                InputStream errStream = process.getErrorStream()) {
            Thread stdThread;
            Thread errThread;
            if(this.jobType == JobType.SPARK) {
                // 开启虚拟线程 获取打印信息 Spark的日志级别是ERROR
                stdThread = Thread.ofVirtual().unstarted(new CollectLogRunnable(stdStream, STD, logConsumer));
                errThread = Thread.ofVirtual().unstarted(new CollectLogRunnable(errStream, STD, logConsumer));
            } else {
                stdThread = Thread.ofVirtual().unstarted(new CollectLogRunnable(stdStream, STD, logConsumer));
                errThread = Thread.ofVirtual().unstarted(new CollectLogRunnable(errStream, ERR, logConsumer));
            }
            try {
                stdThread.start();
                errThread.start();
            } catch (Exception e) {
                log.error("Start log collection thread failed", e);
            }
            log.info("timeoutMills:" + timeoutMills);
            this.exited = process.waitFor(timeoutMills, MILLISECONDS);
            log.info("exited:" + exited);
            log.info("exitValue:" + process.exitValue());
            this.exitValue = exited ? process.exitValue() : EXIT_CODE_FAILURE; // exited是否不为0，不为0的话就赋原值
            this.subprocessIds = CommandUtil.getSubprocessIds(process);

            try {
                stdThread.join(2000);
                stdThread.interrupt();
            } catch (Exception e) {
                log.error("interrupt std log collection thread failed", e);
            }

            try {
                errThread.join(2000);
                errThread.interrupt();
            } catch (Exception e) {
                log.error("interrupt err log collection thread failed", e);
            }
        } finally {
            process.destroy();
        }
    }

    @Override
    public void cancel() {
        if (processId != null) {
            CommandUtil.forceKill(processId);
        }

        if (subprocessIds != null) {
            for (Long subprocessId : subprocessIds) {
                CommandUtil.forceKill(subprocessId);
            }
        }
    }

    public ShellCallback buildShellCallback() {
        ShellCallback callback = new ShellCallback(exited, exitValue, processId);
        callback.setStdMsg(getStdMsg());
        callback.setErrMsg(getErrMsg());
        return callback;
    }

    public ExecutionStatus finalStatus() {
        if (exited) {
            if (exitValue == EXIT_CODE_SUCCESS) {
                return SUCCESS;
            } else if (exitValue == EXIT_CODE_KILLED) {
                return KILLED;
            }
            return FAILURE;
        }

        return KILLABLE;
    }

    public BiConsumer<CmdOutType, String> newLogBuffer(BiConsumer<CmdOutType, String> consumer) {

        return (type, line) -> {
            // call accept method of subclass.
            if (consumer != null) {
                consumer.accept(type, line);
            }
            // buffer message.
            if (type == STD) {
                System.out.println(line);
                stdMsg.append(line); // 将每行加入buffer中
                stdMsg.append(LINE_SEPARATOR);
            } else if (type == ERR) {
                errMsg.append(line);
                errMsg.append(LINE_SEPARATOR);
            }
        };
    }

    public void setLogConsumer(BiConsumer<CmdOutType, String> logConsumer) {
        this.logConsumer = newLogBuffer(logConsumer);
    }

    public String getStdMsg() {
        return stdMsg.toString();
    }

    public String getErrMsg() {
        return errMsg.toString();
    }
}
