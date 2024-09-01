alter table template add column sync_date date comment '同步日期';
alter table template add column  increment_view int comment '新增查看';
alter table template add column  increment_download int comment '新增下载';
alter table template add column  domain varchar(256) comment '模板市场地址';
alter table template add column is_app tinyint(1) comment '是否是应用';

