package io.dataease.constant;

/**
 * Author: wangjiahao
 * Description:
 */
public class CommonConstants {


    //操作类型
    public static final class OPT_TYPE {

        public static final String INSERT = "insert";

        public static final String UPDATE = "update";

        public static final String DELETE = "delete";

        public static final String SELECT = "select";

    }

    //操作类型
    public static final class CHECK_RESULT {

        // 不存在
        public static final String NONE = "none";

        // 全局存在
        public static final String EXIST_ALL = "exist_all";

        // 当前用户存在
        public static final String EXIST_USER = "exist_user";

        // 其他用户存在
        public static final String EXIST_OTHER = "exist_other";

    }

    //图表数据查询来源
    public static final class VIEW_QUERY_FROM {

        // 仪表板
        public static final String PANEL = "panel";

        // 仪表板编辑
        public static final String PANEL_EDIT = "panel_edit";

    }

    //图表数据查询模式
    public static final class VIEW_RESULT_MODE {

        // 所有
        public static final String ALL = "all";

        // 自定义
        public static final String CUSTOM = "custom";
    }

    //图表数据查询来源
    public static final class VIEW_EDIT_FROM {

        // 仪表板
        public static final String PANEL = "panel";

        // 仪表板编辑
        public static final String CACHE = "cache";

    }

    //图表数据读取来源
    public static final class VIEW_DATA_FROM {

        // 模板数据
        public static final String TEMPLATE = "template";

        //数据集数据
        public static final String CHART = "dataset";

    }

    public static final class TEMPLATE_SOURCE {
        //模板市场
        public static final String MARKET = "market";
        //模板管理
        public static final String MANAGE = "manage";
        //公共
        public static final String PUBLIC = "public";
    }

    public static final class RESOURCE_TABLE {
        //主表
        public static final String CORE = "core";
        //镜像表
        public static final String SNAPSHOT = "snapshot";
    }


    public static final class DV_STATUS {
        //未发布
        public static final int UNPUBLISHED = 0;
        //已发布
        public static final int PUBLISHED = 1;
        //已保存未发布
        public static final int SAVED_UNPUBLISHED = 2;
    }

}
