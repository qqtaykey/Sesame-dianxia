package io.github.lazyimmortal.sesame.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import io.github.lazyimmortal.sesame.data.task.ModelTask;
import io.github.lazyimmortal.sesame.entity.UserEntity;
import io.github.lazyimmortal.sesame.util.*;
import io.github.lazyimmortal.sesame.util.idMap.UserIdMap;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class ConfigV2 {

    private static final String TAG = ConfigV2.class.getSimpleName();

    public static final ConfigV2 INSTANCE = new ConfigV2();

    @JsonIgnore
    private boolean init;

    private final Map<String, ModelFields> modelFieldsMap = new ConcurrentHashMap<>();

    public void setModelFieldsMap(Map<String, ModelFields> newModels) {
        modelFieldsMap.clear();
        Map<String, ModelConfig> modelConfigMap = ModelTask.getModelConfigMap();
        if (newModels == null) {
            newModels = new HashMap<>();
        }
        for (ModelConfig modelConfig : modelConfigMap.values()) {
            String modelCode = modelConfig.getCode();
            ModelFields newModelFields = new ModelFields();
            ModelFields configModelFields = modelConfig.getFields();
            ModelFields modelFields = newModels.get(modelCode);
            if (modelFields != null) {
                for (ModelField<?> configModelField : configModelFields.values()) {
                    ModelField<?> modelField = modelFields.get(configModelField.getCode());
                    try {
                        if (modelField != null) {
                            Object value = modelField.getValue();
                            if (value != null) {
                                configModelField.setObjectValue(value);
                            }
                        }
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                    newModelFields.addField(configModelField);
                }
            } else {
                for (ModelField<?> configModelField : configModelFields.values()) {
                    newModelFields.addField(configModelField);
                }
            }
            modelFieldsMap.put(modelCode, newModelFields);
        }
    }

    public Boolean hasModelFields(String modelCode) {
        return modelFieldsMap.containsKey(modelCode);
    }

    /*public ModelFields getModelFields(String modelCode) {
        return modelFieldsMap.get(modelCode);
    }*/

    /*public void removeModelFields(String modelCode) {
        modelFieldsMap.remove(modelCode);
    }*/

    /*public void addModelFields(String modelCode, ModelFields modelFields) {
        modelFieldsMap.put(modelCode, modelFields);
    }*/

    public Boolean hasModelField(String modelCode, String fieldCode) {
        ModelFields modelFields = modelFieldsMap.get(modelCode);
        if (modelFields == null) {
            return false;
        }
        return modelFields.containsKey(fieldCode);
    }

    /*public ModelField getModelField(String modelCode, String fieldCode) {
        ModelFields modelFields = modelFieldsMap.get(modelCode);
        if (modelFields == null) {
            return null;
        }
        return modelFields.get(fieldCode);
    }*/

    /*public void removeModelField(String modelCode, String fieldCode) {
        ModelFields modelFields = getModelFields(modelCode);
        if (modelFields == null) {
            return;
        }
        modelFields.remove(fieldCode);
    }*/

    /*public Boolean addModelField(String modelCode, ModelField modelField) {
        ModelFields modelFields = getModelFields(modelCode);
        if (modelFields == null) {
            return false;
        }
        modelFields.put(modelCode, modelField);
        return true;
    }*/

    /*@SuppressWarnings("unchecked")
    public <T extends ModelField> T getModelFieldExt(String modelCode, String fieldCode) {
        return (T) getModelField(modelCode, fieldCode);
    }*/

    public static Boolean isModify(String userId) {
        String json = null;
        File configV2File;
        if (StringUtil.isEmpty(userId)) {
            configV2File = FileUtil.getDefaultConfigV2File();
        } else {
            configV2File = FileUtil.getConfigV2File(userId);
        }
        if (configV2File.exists()) {
            json = FileUtil.readFromFile(configV2File);
        }
        if (json != null) {
            String formatted = toSaveStr();
            return formatted == null || !formatted.equals(json);
        }
        return true;
    }

    public static Boolean save(String userId, Boolean force) {
        if (!force) {
            if (!isModify(userId)) {
                return true;
            }
        }
        String json = toSaveStr();
        boolean success;
        if (StringUtil.isEmpty(userId)) {
            userId = "默认";
            success = FileUtil.setDefaultConfigV2File(json);
        } else {
            success = FileUtil.setConfigV2File(userId, json);
        }
        
        // ========== 新增：保存成功后触发滚动备份 ==========
        if (success) {
            FileUtil.backupConfigV2WithRolling(userId);
        }
        
        Log.record("保存配置: " + userId);
        return success;
    }
    
    public static synchronized ConfigV2 load(String userId) {
        Log.i(TAG, "开始加载配置");
        String userName = "";
        File configV2File = null;
        try {
            if (StringUtil.isEmpty(userId)) {
                configV2File = FileUtil.getDefaultConfigV2File();
                userName = "默认";
            } else {
                configV2File = FileUtil.getConfigV2File(userId);
                UserEntity userEntity = UserIdMap.get(userId);
                if (userEntity == null) {
                    userName = userId;
                } else {
                    userName = userEntity.getShowName();
                }
            }
            Log.record("加载配置: " + userName);
            if (configV2File.exists()) {
                String json = FileUtil.readFromFile(configV2File);
                JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                String formatted = toSaveStr();
                if (formatted != null && !formatted.equals(json)) {
                    Log.i(TAG, "格式化配置: " + userName);
                    Log.system(TAG, "格式化配置: " + userName);
                    FileUtil.write2File(formatted, configV2File);
                }
            } else {
                File defaultConfigV2File = FileUtil.getDefaultConfigV2File();
                if (defaultConfigV2File.exists()) {
                    String json = FileUtil.readFromFile(defaultConfigV2File);
                    JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                    Log.i(TAG, "复制新配置: " + userName);
                    Log.system(TAG, "复制新配置: " + userName);
                    FileUtil.write2File(json, configV2File);
                } else {
                    unload();
                    Log.i(TAG, "初始新配置: " + userName);
                    Log.system(TAG, "初始新配置: " + userName);
                    FileUtil.write2File(toSaveStr(), configV2File);
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            Log.i(TAG, "重置配置: " + userName);
            Log.system(TAG, "重置配置: " + userName);
            try {
                // 检查当前配置文件是否是默认配置
                boolean isDefaultConfig = isCurrentConfigDefault(configV2File);
                
                if (!isDefaultConfig) {
                    // 当前配置不是默认配置，说明用户手动关闭模块，不应该恢复
                    Log.i(TAG, "当前配置不是默认配置，不执行恢复: " + userName);
                    Log.system(TAG, "当前配置不是默认配置，不执行恢复: " + userName);
                    // 不要调用unload()，直接返回当前实例
                    return INSTANCE;
                }
                
                // 当前配置是默认配置，说明配置文件被重置了，需要从备份恢复
                Log.i(TAG, "当前配置是默认配置，尝试从备份恢复: " + userName);
                Log.system(TAG, "当前配置是默认配置，尝试从备份恢复: " + userName);
                
                // 尝试从备份恢复，按时间顺序从新到旧逐个尝试
                File[] backupFiles = findAllBackupFiles(userId);
                Log.i(TAG, "找到 " + (backupFiles != null ? backupFiles.length : 0) + " 个备份文件");
                Log.system(TAG, "找到 " + (backupFiles != null ? backupFiles.length : 0) + " 个备份文件");
                boolean restoreSuccess = false;
                
                if (backupFiles != null && backupFiles.length > 0) {
                    for (File backupFile : backupFiles) {
                        try {
                            Log.i(TAG, "尝试从备份恢复配置: " + backupFile.getName() + " (修改时间: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(backupFile.lastModified())) + ")");
                            Log.system(TAG, "尝试从备份恢复配置: " + backupFile.getName());
                            
                            String backupJson = FileUtil.readFromFile(backupFile);
                            if (backupJson == null || backupJson.isEmpty()) {
                                Log.i(TAG, "备份文件为空，跳过: " + backupFile.getName());
                                continue;
                            }
                            
                            Log.i(TAG, "备份文件大小: " + backupJson.length() + " 字符");
                            
                            // 验证备份文件是否有效
                            if (!isValidBackupJson(backupJson)) {
                                Log.i(TAG, "备份文件格式无效，跳过: " + backupFile.getName());
                                continue;
                            }
                            
                            // 检查备份文件是否是默认配置
                            if (isBackupConfigDefault(backupJson)) {
                                Log.i(TAG, "备份文件是默认配置，跳过: " + backupFile.getName());
                                continue;
                            }
                            
                            Log.i(TAG, "备份文件不是默认配置，开始恢复: " + backupFile.getName());
                            JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(backupJson);
                            
                            // 恢复成功，保存到主配置文件
                            if (configV2File != null) {
                                FileUtil.write2File(toSaveStr(), configV2File);
                                Log.i(TAG, "从备份恢复配置成功: " + userName + " (备份文件: " + backupFile.getName() + ")");
                                Log.system(TAG, "从备份恢复配置成功: " + userName + " (备份文件: " + backupFile.getName() + ")");
                                restoreSuccess = true;
                                break;
                            }
                        } catch (Exception e) {
                            Log.i(TAG, "恢复备份失败: " + backupFile.getName() + ", 原因: " + e.getMessage());
                            Log.printStackTrace(TAG, e);
                        }
                    }
                }
                
                // 所有备份都恢复失败，执行重置
                if (!restoreSuccess) {
                    Log.i(TAG, "所有备份恢复失败，使用默认配置: " + userName);
                    Log.system(TAG, "所有备份恢复失败，使用默认配置: " + userName);
                    unload();
                    if (configV2File != null) {
                        FileUtil.write2File(toSaveStr(), configV2File);
                    }
                }
            } catch (Exception e) {
                Log.printStackTrace(TAG, e);
                // 备份恢复失败，执行重置
                try {
                    unload();
                    if (configV2File != null) {
                        FileUtil.write2File(toSaveStr(), configV2File);
                    }
                } catch (Exception ex) {
                    Log.printStackTrace(TAG, ex);
                }
            }
        }
        INSTANCE.setInit(true);
        Log.i(TAG, "加载配置结束");
        return INSTANCE;
    }
    
    /**
     * 从备份恢复配置
     * @param userId 用户ID
     * @return 是否恢复成功
     */
    public static boolean restoreFromBackup(String userId) {
        String userName = "";
        File configV2File = null;
        try {
            if (StringUtil.isEmpty(userId)) {
                configV2File = FileUtil.getDefaultConfigV2File();
                userName = "默认";
            } else {
                configV2File = FileUtil.getConfigV2File(userId);
                UserEntity userEntity = UserIdMap.get(userId);
                if (userEntity == null) {
                    userName = userId;
                } else {
                    userName = userEntity.getShowName();
                }
            }
            
            // 尝试从备份恢复，按时间顺序从新到旧逐个尝试
            File[] backupFiles = findAllBackupFiles(userId);
            Log.i(TAG, "找到 " + (backupFiles != null ? backupFiles.length : 0) + " 个备份文件");
            Log.system(TAG, "找到 " + (backupFiles != null ? backupFiles.length : 0) + " 个备份文件");
            
            if (backupFiles != null && backupFiles.length > 0) {
                for (File backupFile : backupFiles) {
                    try {
                        Log.i(TAG, "尝试从备份恢复配置: " + backupFile.getName() + " (修改时间: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(backupFile.lastModified())) + ")");
                        Log.system(TAG, "尝试从备份恢复配置: " + backupFile.getName());
                        
                        String backupJson = FileUtil.readFromFile(backupFile);
                        if (backupJson == null || backupJson.isEmpty()) {
                            Log.i(TAG, "备份文件为空，跳过: " + backupFile.getName());
                            continue;
                        }
                        
                        Log.i(TAG, "备份文件大小: " + backupJson.length() + " 字符");
                        
                        // 验证备份文件是否有效
                        if (!isValidBackupJson(backupJson)) {
                            Log.i(TAG, "备份文件格式无效，跳过: " + backupFile.getName());
                            continue;
                        }
                        
                        // 检查备份文件是否是默认配置
                        if (isBackupConfigDefault(backupJson)) {
                            Log.i(TAG, "备份文件是默认配置，跳过: " + backupFile.getName());
                            continue;
                        }
                        
                        Log.i(TAG, "备份文件不是默认配置，开始恢复: " + backupFile.getName());
                        JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(backupJson);
                        
                        // 恢复成功，保存到主配置文件
                        if (configV2File != null) {
                            FileUtil.write2File(toSaveStr(), configV2File);
                            Log.i(TAG, "从备份恢复配置成功: " + userName + " (备份文件: " + backupFile.getName() + ")");
                            Log.system(TAG, "从备份恢复配置成功: " + userName + " (备份文件: " + backupFile.getName() + ")");
                            return true;
                        }
                    } catch (Exception e) {
                        Log.i(TAG, "恢复备份失败: " + backupFile.getName() + ", 原因: " + e.getMessage());
                        Log.printStackTrace(TAG, e);
                    }
                }
            }
            
            // 所有备份都恢复失败
            Log.i(TAG, "所有备份恢复失败，使用默认配置: " + userName);
            Log.system(TAG, "所有备份恢复失败，使用默认配置: " + userName);
            return false;
            
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
            return false;
        }
    }
    
    /**
     * 查找所有备份文件并按时间排序（从新到旧），最多返回5个
     * @param userId 用户ID
     * @return 排序后的备份文件数组
     */
    public static File[] findAllBackupFiles(String userId) {
        String safeUserId = StringUtil.isEmpty(userId) ? "default" : userId;
        File backupDir = FileUtil.getBackupDirectoryFile();
        int maxCount = FileUtil.getBackupMaxCountFromConfig();
        
        java.util.List<File> backupFiles = new java.util.ArrayList<>();
        
        for (int i = 0; i < maxCount; i++) {
            String suffix = FileUtil.getBackupSuffix(i);
            File file = new File(backupDir, FileUtil.BACKUP_FILE_PREFIX + safeUserId + "_" + suffix + FileUtil.BACKUP_FILE_EXT);
            if (file.exists()) {
                backupFiles.add(file);
            }
        }
        
        // 按修改时间从新到旧排序
        backupFiles.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        
        // 限制最多返回5个备份文件
        int limit = Math.min(5, backupFiles.size());
        if (limit > 0) {
            backupFiles = backupFiles.subList(0, limit);
        }
        
        return backupFiles.toArray(new File[0]);
    }
    
    /**
     * 检查当前配置文件是否是默认配置
     * @param configFile 配置文件
     * @return 是否是默认配置
     */
    public static boolean isCurrentConfigDefault(File configFile) {
        try {
            if (configFile == null || !configFile.exists()) {
                return true;
            }
            
            // 读取当前配置
            String currentConfig = FileUtil.readFromFile(configFile);
            if (currentConfig == null || currentConfig.isEmpty()) {
                return true;
            }
            
            // 创建临时实例来生成默认配置，避免影响当前实例
            ConfigV2 tempConfig = new ConfigV2();
            String defaultConfig = tempConfig.toSaveStr();
            
            // 比较配置是否相同
            return currentConfig.equals(defaultConfig);
            
        } catch (Exception e) {
            // 检查失败，保守起见认为是默认配置
            Log.i(TAG, "检查配置是否为默认配置失败: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * 检查备份文件是否是默认配置
     * @param backupJson 备份JSON字符串
     * @return 是否是默认配置
     */
    public static boolean isBackupConfigDefault(String backupJson) {
        try {
            if (backupJson == null || backupJson.isEmpty()) {
                return true;
            }
            
            // 创建临时实例来生成默认配置，避免影响当前实例
            ConfigV2 tempConfig = new ConfigV2();
            String defaultConfig = tempConfig.toSaveStr();
            
            // 比较配置是否相同
            return backupJson.equals(defaultConfig);
            
        } catch (Exception e) {
            // 检查失败，保守起见认为是默认配置
            Log.i(TAG, "检查备份是否为默认配置失败: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * 验证备份JSON是否有效
     * @param json 备份JSON字符串
     * @return 是否有效
     */
    public static boolean isValidBackupJson(String json) {
        if (json == null || json.isEmpty()) {
            return false;
        }
        // 检查是否包含必要的配置字段
        return json.contains("modelFieldsMap");
    }
    
    /**
     * 查找最新的备份文件
     * @param userId 用户ID
     * @return 最新的备份文件
     */
    private static File findLatestBackupFile(String userId) {
        return FileUtil.findLatestBackupFile(userId);
    }

    public static synchronized void unload() {
        for (ModelFields modelFields : INSTANCE.modelFieldsMap.values()) {
            for (ModelField<?> modelField : modelFields.values()) {
                if (modelField != null) {
                    modelField.reset();
                }
            }
        }
    }

    public static String toSaveStr() {
        return JsonUtil.toFormatJsonString(INSTANCE);
    }

}
