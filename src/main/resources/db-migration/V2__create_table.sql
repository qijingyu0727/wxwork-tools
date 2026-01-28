drop table if exists `customer_group_info`;
CREATE TABLE if not exists `customer_group_info` (
                                       `room_id` varchar(256) NOT NULL,
                                       `room_name` varchar(256) DEFAULT NULL,
                                       PRIMARY KEY (`room_id`)
) ENGINE=InnoDB ;

drop table if exists `archive_msg_info`;
CREATE TABLE if not exists  `archive_msg_info` (
                                    `seq` int(11) NOT NULL,
                                    `publickey_ver` int(11) DEFAULT NULL,
                                    `room_id` varchar(256) DEFAULT NULL,
                                    `msg_time` bigint(20) DEFAULT NULL,
                                    `context` varchar(1024) DEFAULT NULL,
                                    PRIMARY KEY (`seq`)
) ENGINE=InnoDB ;