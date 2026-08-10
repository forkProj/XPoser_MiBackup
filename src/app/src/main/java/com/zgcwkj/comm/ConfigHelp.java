package com.zgcwkj.comm;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * 配置文件读写工具
 * 配置文件位于 /sdcard/MIUI/backup/config.ini，格式为每行 key=value
 */
public class ConfigHelp {

    private static final String TAG = "XpMiBackup";
    public static final String BACKUP_ROOT = "/sdcard/MIUI/backup";
    private static final String CONFIG_PATH = BACKUP_ROOT + "/config.ini";

    // 配置缓存：按文件修改时间和大小失效，避免上传/下载热路径里反复读盘解析
    private static volatile JSONObject sConfigCache;
    private static volatile long sConfigLastModified;
    private static volatile long sConfigLength;

    /**
     * 加载配置并补齐默认值
     * 文件不存在或部分 key 缺失时，调用方仍能拿到完整配置
     * 命中缓存时返回副本，避免调用方误改缓存内容
     */
    public static JSONObject load() {
        var file = new File(CONFIG_PATH);
        var modified = file.exists() ? file.lastModified() : -1L;
        var length = file.exists() ? file.length() : 0L;
        var cached = sConfigCache;
        if (cached != null && modified == sConfigLastModified && length == sConfigLength) {
            return copyOf(cached);
        }

        var map = new LinkedHashMap<String, String>();
        if (file.exists()) {
            try (var reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                var line = reader.readLine();
                while (line != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                        var idx = line.indexOf('=');
                        map.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
                    }
                    line = reader.readLine();
                }
            } catch (Exception e) {
                LogHelp.e(TAG, "load config failed: " + e.getMessage(), e);
            }
        }

        var defaults = defaultMap();
        for (var entry : defaults.entrySet()) {
            if (!map.containsKey(entry.getKey())) {
                map.put(entry.getKey(), entry.getValue());
            }
        }

        var json = new JSONObject();
        for (var entry : map.entrySet()) {
            try {
                json.put(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                LogHelp.e(TAG, "put config value failed: " + entry.getKey(), e);
            }
        }
        sConfigCache = json;
        sConfigLastModified = modified;
        sConfigLength = length;
        return copyOf(json);
    }

    /**
     * 保存配置为 INI 风格文本
     * 先创建父目录再打开文件，避免首次保存时 FileWriter 因目录不存在而失败
     */
    public static void save(JSONObject json) {
        var file = new File(CONFIG_PATH);
        var dir = file.getParentFile();
        try {
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
        } catch (Exception e) {
            LogHelp.e(TAG, "create config dir failed: " + e.getMessage(), e);
            return;
        }

        try (var writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            var keys = json.keys();
            while (keys.hasNext()) {
                var key = keys.next();
                var val = json.opt(key);
                writer.write(key + "=" + (val != null ? val.toString() : ""));
                writer.newLine();
            }
            writer.flush();
        } catch (Exception e) {
            LogHelp.e(TAG, "save config failed: " + e.getMessage(), e);
            return;
        }
        // 写入成功后同步刷新缓存，避免按mtime/size无法区分同秒内两次写入的场景
        sConfigCache = copyOf(json);
        sConfigLastModified = file.lastModified();
        sConfigLength = file.length();
    }

    /**
     * 深拷贝JSONObject，避免调用方误改缓存内容
     */
    private static JSONObject copyOf(JSONObject source) {
        try {
            return new JSONObject(source.toString());
        } catch (Exception e) {
            return source;
        }
    }

    /**
     * 读取字符串配置
     */
    public static String getString(String key, String def) {
        return load().optString(key, def);
    }

    /**
     * 读取整数配置，解析失败时使用调用方提供的默认值
     */
    public static int getInt(String key, int def) {
        try {
            return Integer.parseInt(load().optString(key, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * 默认配置值
     */
    private static LinkedHashMap<String, String> defaultMap() {
        var map = new LinkedHashMap<String, String>();
        map.put("device_id", "zgcwkj");
        map.put("device_name", isChineseLocale() ? "云端备份设备" : "Cloud backup device");
        map.put("device_describe", isChineseLocale() ? "我的云端备份设备" : "My cloud backup device");
        map.put("backup_path", "MIUI/backup");
        map.put("backup_max", "5");
        map.put("log_enabled", "false");
        map.put("protocol", "smb");
        map.put("upload_threads", "3");
        map.put("chunk_size_mb", "64");
        map.put("smb_server", "192.168.68.1");
        map.put("smb_port", "445");
        map.put("smb_share", isChineseLocale() ? "备份数据" : "BackupData");
        map.put("smb_user", "");
        map.put("smb_pass", "");
        map.put("webdav_url", "https://192.168.1.1:8080/dav");
        map.put("webdav_user", "");
        map.put("webdav_pass", "");
        map.put("custom_script_b64", "");
        return map;
    }

    private static boolean isChineseLocale() {
        return "zh".equalsIgnoreCase(Locale.getDefault().getLanguage());
    }
}
