drop table template_label;
CREATE TABLE `template_label` (
                                  `template_info` varchar(512) DEFAULT NULL COMMENT '模版信息',
                                  `label` varchar(50) NOT NULL COMMENT '标签'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模版标签'
