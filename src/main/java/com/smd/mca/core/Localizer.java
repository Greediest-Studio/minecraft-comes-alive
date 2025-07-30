package com.smd.mca.core;

import com.google.common.base.Charsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.LanguageManager;
import net.minecraft.util.StringUtils;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class Localizer {
    private Map<String, String> localizerMap = new HashMap<>();
    private static final ArrayList<String> EMPTY_LIST = new ArrayList<>();

    public Localizer() {
        InputStream inStream = null;
        try {

            LanguageManager languageManager = Minecraft.getMinecraft().getLanguageManager();
            String currentLangCode = languageManager.getCurrentLanguage().getLanguageCode();

            String langFilePath = String.format("/assets/mca/lang/%s.lang", currentLangCode);
            inStream = StringUtils.class.getResourceAsStream(langFilePath);

            if (inStream == null) {
                MCA.getLog().warn("语言文件未找到：" + langFilePath + "，回退至默认语言 en_us.lang");
                inStream = StringUtils.class.getResourceAsStream("/assets/mca/lang/en_us.lang");
            }

            if (inStream == null) {
                MCA.getLog().error("默认语言文件未找到：/assets/mca/lang/en_us.lang");
                return;
            }

            List<String> lines = IOUtils.readLines(inStream, Charsets.UTF_8);

            for (String line : lines) {
                if (line.startsWith("#") || line.isEmpty()) continue;
                String[] split = line.split("=", 2); // 避免值中出现 "=" 导致 split 数组越界
                if (split.length < 2) continue;
                localizerMap.put(split[0], split[1]);
            }

        } catch (IOException e) {
            MCA.getLog().error("初始化语言文件失败：" + e.getMessage());
        }
    }

    public String localize(String key, String... vars) {
        ArrayList<String> list = new ArrayList<>();
        Collections.addAll(list, vars);
        return localize(key, vars != null ? list : EMPTY_LIST);
    }

    public String localize(String key, ArrayList<String> vars) {
        String result = localizerMap.getOrDefault(key, key);
        if (result.equals(key)) {
            List<String> responses = localizerMap.entrySet().stream()
                    .filter(entry -> entry.getKey().contains(key))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
            if (!responses.isEmpty()) {
                result = responses.get(new Random().nextInt(responses.size()));
            }
        }
        return parseVars(result, vars).replaceAll("\\\\", "");
    }

    private String parseVars(String str, ArrayList<String> vars) {
        int index = 1;
        str = str.replaceAll("%Supporter%", MCA.getInstance().getRandomSupporter());

        String varString = "%v" + index + "%";
        while (str.contains("%v") && index < 10) {
            try {
                str = str.replaceAll(varString, vars.get(index - 1));
            } catch (IndexOutOfBoundsException e) {
                str = str.replaceAll(varString, "");
                MCA.getLog().warn("替换变量失败：" + varString + " 在字符串中：" + str);
            } finally {
                index++;
                varString = "%v" + index + "%";
            }
        }

        return str;
    }
}
