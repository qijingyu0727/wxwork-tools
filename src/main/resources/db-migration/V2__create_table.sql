drop table if exists `smart_form_record`;
CREATE TABLE if not exists `smart_form_record` (
`id` int auto_increment,
`doc_name`     varchar(256) NOT NULL,
`doc_id`     varchar(256) NOT NULL,
`admin_phone_numbers` varchar(256) NOT NULL,
`admin_user_ids` varchar(256) DEFAULT NULL,
 PRIMARY KEY (`id`)
) ENGINE=InnoDB ;
