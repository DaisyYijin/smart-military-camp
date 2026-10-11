package com.szjx.personnel.server.launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 端口探测工具:空闲检测 / 本程序实例识别 / 占用进程识别 / 空闲端口扫描
 */
public final class PortProbe {

    /** /api/ping 应答中的签名,用于判断端口上的服务是不是本程序 */
    public static final String APP_SIGNATURE = "personnel-server";

    private static final Pattern PID_NAME = Pattern.compile("pid=(\\d+)");

    private PortProbe() {
    }

    /** 端口是否空闲(绑定测试,与 Tomcat 监听 0.0.0.0 行为一致) */
    public static boolean isFree(int port) {
        try {
            ServerSocket ss = new ServerSocket(port);
            ss.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 从 from 起向上找 count 个空闲端口,供用户挑选 */
    public static List<Integer> findFreePorts(int from, int count) {
        List<Integer> free = new ArrayList<Integer>();
        int port = from;
        while (free.size() < count && port < 65535) {
            port++;
            if (isFree(port)) {
                free.add(port);
            }
        }
        return free;
    }

    /** 端口上是否是本程序已运行的实例(通过 /api/ping 应答签名判断,跨平台可靠) */
    public static boolean isOurApp(int port) {
        try {
            String body = httpGet("http://127.0.0.1:" + port + "/api/ping", 1500);
            return body != null && body.contains(APP_SIGNATURE);
        } catch (Exception e) {
            return false;
        }
    }

    /** 等待服务启动就绪 */
    public static boolean waitReady(int port, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try {
                String body = httpGet("http://127.0.0.1:" + port + "/api/ping", 1000);
                if (body != null && body.contains(APP_SIGNATURE)) {
                    return true;
                }
            } catch (Exception ignore) {
                // 尚未启动完成,继续等
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * 端口被非本程序占用时,识别占用进程,如 "nginx.exe (PID 1234)";
     * 无法识别(权限不足/命令缺失)返回 null,调用方按"未知程序"处理
     */
    public static String describeOccupier(int port) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                return describeOccupierWindows(port);
            }
            return describeOccupierLinux(port);
        } catch (Exception e) {
            return null;
        }
    }

    private static String describeOccupierWindows(int port) {
        List<String> parts = new ArrayList<String>();
        for (String line : runCommand(new String[]{"netstat", "-ano", "-p", "tcp"})) {
            String[] t = line.trim().split("\\s+");
            if (t.length >= 5 && "LISTENING".equalsIgnoreCase(t[3]) && endsWithPort(t[1], port)) {
                String pid = t[4];
                parts.add(processNameWindows(pid) + " (PID " + pid + ")");
            }
        }
        return parts.isEmpty() ? null : join(parts);
    }

    private static String describeOccupierLinux(int port) {
        List<String> lines = runCommand(new String[]{"ss", "-tlnp"});
        if (lines.isEmpty()) {
            lines = runCommand(new String[]{"netstat", "-tlnp"});
        }
        List<String> parts = new ArrayList<String>();
        for (String line : lines) {
            String[] t = line.trim().split("\\s+");
            // ss:   LISTEN 0 5 0.0.0.0:8080 0.0.0.0:* users:(("nginx",pid=1234,fd=6))
            // netstat: tcp 0 0 0.0.0.0:8080 0.0.0.0:* LISTEN 1234/nginx
            if (t.length < 4 || !endsWithPort(t[3], port)) {
                continue;
            }
            Matcher m = PID_NAME.matcher(line);
            if (m.find()) {
                String pid = m.group(1);
                String name = extractLinuxProcessName(line);
                parts.add((name.isEmpty() ? "未知程序" : name) + " (PID " + pid + ")");
            } else if (t.length >= 7 && t[t.length - 1].contains("/")) {
                String[] pidName = t[t.length - 1].split("/", 2);
                parts.add(pidName[1] + " (PID " + pidName[0] + ")");
            }
        }
        return parts.isEmpty() ? null : join(parts);
    }

    private static String extractLinuxProcessName(String line) {
        // users:(("nginx",pid=1234,fd=6)) → nginx
        int at = line.indexOf("users:((\"");
        if (at >= 0) {
            int start = at + 9;
            int end = line.indexOf('"', start);
            if (end > start) {
                return line.substring(start, end);
            }
        }
        return "";
    }

    private static String processNameWindows(String pid) {
        for (String line : runCommand(new String[]{"tasklist", "/fo", "csv", "/nh", "/fi", "PID eq " + pid})) {
            line = line.trim();
            if (line.startsWith("\"")) {
                int end = line.indexOf('"', 1);
                if (end > 1) {
                    return line.substring(1, end);
                }
            }
        }
        return "未知程序";
    }

    private static boolean endsWithPort(String addressToken, int port) {
        return addressToken != null && addressToken.endsWith(":" + port);
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private static String httpGet(String url, int timeoutMillis) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(timeoutMillis);
        conn.setReadTimeout(timeoutMillis);
        try {
            if (conn.getResponseCode() != 200) {
                return null;
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    /** 执行系统命令并按行返回输出;命令不存在或超时返回已读到的内容 */
    private static List<String> runCommand(String[] cmd) {
        List<String> lines = new ArrayList<String>();
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            final Process p = pb.start();
            // 独立线程兜底:命令挂住时 5 秒后强杀,避免阻塞启动
            Thread killer = new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(5000);
                        p.destroy();
                    } catch (InterruptedException ignore) {
                        // 正常结束被中断,无需处理
                    }
                }
            });
            killer.setDaemon(true);
            killer.start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            p.waitFor(5, TimeUnit.SECONDS);
            killer.interrupt();
        } catch (Exception ignore) {
            // 命令不可用等情形,交由调用方按"识别失败"处理
        }
        return lines;
    }
}
