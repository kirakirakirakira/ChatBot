package com.chatbot.chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 给 message.content 补 ngram 全文索引。
 * <p>
 * 为什么不在 @Table 上声明：Hibernate 没有跨库的「FULLTEXT WITH PARSER ngram」注解，
 * 而 ngram 是中文检索的硬前提（默认分词器按空格切，中文整段会被当成一个词，MATCH 基本命中不了）。
 * <p>
 * 为什么用 ApplicationRunner 而不是只写进 sql/init.sql：本项目允许 ddl-auto=update 直接起库，
 * 不是每个环境都会执行 init.sql。启动时幂等检查一次，已有索引就跳过。
 * 建不出来只 warn、不阻断启动——HistorySearchService 会自动回退 LIKE。
 */
@Component
public class MessageSearchIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MessageSearchIndexInitializer.class);
    private static final String INDEX_NAME = "ft_message_content";

    private final JdbcTemplate jdbcTemplate;

    public MessageSearchIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer existing = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.statistics "
                            + "where table_schema = database() and table_name = 'message' and index_name = ?",
                    Integer.class, INDEX_NAME);
            if (existing != null && existing > 0) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE `message` ADD FULLTEXT INDEX `" + INDEX_NAME
                    + "` (`content`) WITH PARSER ngram");
            log.info("已为 message.content 创建 ngram 全文索引");
        } catch (Exception e) {
            // 库不是 MySQL / 没装 ngram / 没 DDL 权限都会走到这里，都不该拦住应用启动
            log.warn("message.content 全文索引不可用，历史检索将回退 LIKE: {}", e.getMessage());
        }
    }
}
