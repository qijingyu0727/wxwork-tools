drop table if exists `template`;
CREATE TABLE `template`
(
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        varchar(255) NOT NULL COMMENT '名称',
    `description` varchar(255) DEFAULT NULL COMMENT '描述',
    `type`        varchar(50)  NOT NULL COMMENT '类型(PANEL, SCREEN, APPS, INDUSTRY)',
    `version`     varchar(50)  NOT NULL COMMENT '版本(v1, v2)',
    `update_time` bigint       default NULL COMMENT '模版更新时间',
    `view`        int(10) COMMENT '查看次数',
    `download`    int(10) COMMENT '下载次数',
    PRIMARY KEY (`id`),
    KEY           `version_name_index` (`version`, `name`)
) ENGINE=InnoDB COMMENT='模版信息';

drop table if exists `template_label`;
CREATE TABLE `template_label`
(
    `template_id` bigint COMMENT '模版id',
    `label`       varchar(50) NOT NULL COMMENT '标签'
) ENGINE=InnoDB COMMENT='模版标签';
