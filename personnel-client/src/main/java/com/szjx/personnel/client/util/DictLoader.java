package com.szjx.personnel.client.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.szjx.personnel.client.model.DictItem;
import com.szjx.personnel.client.net.ApiClient;

import javax.swing.JComboBox;
import javax.swing.SwingWorker;
import java.util.List;

/**
 * 数据字典加载工具:异步把字典项填充到下拉框
 */
public final class DictLoader {

    private DictLoader() {
    }

    /**
     * 加载字典到下拉框
     * @param api      API 客户端
     * @param type     字典类型(leave_type / fitness_item / fitness_grade / personnel_status / rank)
     * @param box      目标下拉框
     * @param prefix   首项占位文本(如"全部状态"),可为 null
     * @param onLoaded 加载完成后的回调(用于编辑回填),可为 null
     */
    public static void load(final ApiClient api, final String type, final JComboBox<String> box,
                            final String prefix, final Runnable onLoaded) {
        box.removeAllItems();
        if (prefix != null && !prefix.isEmpty()) {
            box.addItem(prefix);
        }
        new SwingWorker<List<DictItem>, Void>() {
            @Override
            protected List<DictItem> doInBackground() throws Exception {
                JsonObject resp = api.get("/api/dict/" + type);
                return new Gson().fromJson(resp.get("data"),
                        new com.google.gson.reflect.TypeToken<List<DictItem>>() {
                        }.getType());
            }

            @Override
            protected void done() {
                try {
                    List<DictItem> items = get();
                    for (DictItem d : items) {
                        box.addItem(d.getDictLabel());
                    }
                    if (onLoaded != null) {
                        onLoaded.run();
                    }
                } catch (Exception e) {
                    // 字典加载失败不阻塞界面
                }
            }
        }.execute();
    }
}
