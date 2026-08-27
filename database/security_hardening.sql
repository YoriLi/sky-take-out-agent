-- Security / performance hardening for existing installations
-- Run after sky.sql if the schema was already imported.

ALTER TABLE `employee` MODIFY COLUMN `password` varchar(100) COLLATE utf8_bin NOT NULL COMMENT '密码(BCrypt/MD5)';

-- Upgrade legacy plaintext admin password to MD5('123456') when still plaintext
UPDATE `employee` SET `password` = 'e10adc3949ba59abbe56e057f20f883e'
WHERE `username` = 'admin' AND `password` = '123456';

-- Unique / lookup indexes
ALTER TABLE `user` ADD UNIQUE INDEX `idx_openid` (`openid`);
ALTER TABLE `orders` ADD UNIQUE INDEX `idx_orders_number` (`number`);
ALTER TABLE `orders` ADD INDEX `idx_orders_user_id` (`user_id`);
ALTER TABLE `orders` ADD INDEX `idx_orders_status` (`status`);
ALTER TABLE `orders` ADD INDEX `idx_orders_order_time` (`order_time`);
ALTER TABLE `address_book` ADD INDEX `idx_address_user_id` (`user_id`);
ALTER TABLE `shopping_cart` ADD INDEX `idx_cart_user_id` (`user_id`);
ALTER TABLE `dish` ADD INDEX `idx_dish_category_id` (`category_id`);
ALTER TABLE `dish_flavor` ADD INDEX `idx_flavor_dish_id` (`dish_id`);
ALTER TABLE `order_detail` ADD INDEX `idx_detail_order_id` (`order_id`);
ALTER TABLE `setmeal_dish` ADD INDEX `idx_setmeal_dish_setmeal_id` (`setmeal_id`);
ALTER TABLE `setmeal_dish` ADD INDEX `idx_setmeal_dish_dish_id` (`dish_id`);
