package uz.shukrullaev.com.talksy

import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.support.JpaEntityInformation
import org.springframework.data.jpa.repository.support.SimpleJpaRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Base repository interface with soft delete.
 */
@NoRepositoryBean
interface BaseRepository<T : BaseEntity> : JpaRepository<T, Long>, JpaSpecificationExecutor<T> {
    fun findByIdAndDeletedFalse(id: Long): T?
    fun existsByIdAndDeletedFalse(id: Long): Boolean?
    fun trash(id: Long): T?
    fun trashList(ids: List<Long>): List<T?>
    fun findAllNotDeleted(): List<T>
    fun findAllNotDeleted(pageable: Pageable): Page<T>
}

/**
 * Base repository implementation with Specification filter.
 */
class BaseRepositoryImpl<T : BaseEntity>(
    entityInformation: JpaEntityInformation<T, Long>,
    entityManager: EntityManager,
) : SimpleJpaRepository<T, Long>(entityInformation, entityManager), BaseRepository<T> {

    val isNotDeletedSpecification = Specification<T> { root, _, cb ->
        cb.equal(root.get<Boolean>("deleted"), false)
    }

    override fun findByIdAndDeletedFalse(id: Long) =
        findByIdOrNull(id)?.run { if (deleted) null else this }

    override fun existsByIdAndDeletedFalse(id: Long) =
        findByIdOrNull(id)?.run { !deleted }

    @Transactional
    override fun trash(id: Long): T? =
        findByIdOrNull(id)?.takeIf { !it.deleted }?.run {
            deleted = true
            save(this)
        }

    override fun trashList(ids: List<Long>): List<T?> = ids.map { trash(it) }

    override fun findAllNotDeleted(): List<T> = findAll(isNotDeletedSpecification)

    override fun findAllNotDeleted(pageable: Pageable): Page<T> =
        findAll(isNotDeletedSpecification, pageable)
}

// =================== REPOSITORIES ===================

@Repository
interface UserRepository : BaseRepository<User> {
    fun findAllByIdInAndDeletedFalse(ids: Set<Long>): List<User>
    fun findByTelegramIdAndDeletedFalse(telegramId: String): User?
    fun findByUsername(username: String): User?
    fun findByUsernameAndDeletedFalse(username: String): User?
    fun existsByUsernameAndDeletedFalse(username: String): Boolean

    @Query(
        "select u from User u " +
                "where u.deleted = false and lower(u.username) like lower(concat('%', :keyword, '%'))"
    )
    fun searchByUsername(@Param("keyword") keyword: String): List<User>
}

@Repository
interface UserFileRepository : BaseRepository<UserFile> {
    fun findBySha256Hash(hash: String): UserFile?
    fun existsByOwnerIdAndSha256Hash(userId: Long, hash: String): Boolean
}

@Repository
interface ChatRepository : BaseRepository<Chat> {
    fun findAllByIsGroupAndDeletedFalse(isGroup: Boolean): List<Chat>

    @Query(
        """
    select c from Chat c 
        join ChatUser cu1 on cu1.chat = c
        join ChatUser cu2 on cu2.chat = c
            where c.isGroup = false
              and cu1.user.id = :user1Id 
              and cu2.user.id = :user2Id
         """
    )
    fun findDirectChatBetween(user1Id: Long, user2Id: Long): Chat?

    @Query(
        value = """
        SELECT
            c.id              AS chatId,
            c.title           AS title,
            c.is_group        AS isGroup,
            MAX(m.created_date) AS lastMessageTime,
            COUNT(CASE WHEN ms.status = 'SENT' AND ms.user_id = :userId THEN 1 END) AS newMessages
        FROM chats c
        JOIN chat_users cu
            ON cu.chat_id = c.id
            AND cu.user_id = :userId
            AND cu.deleted = false
        LEFT JOIN messages m
            ON m.chat_id = c.id
            AND m.deleted = false
        LEFT JOIN message_statuses ms
            ON ms.message_id = m.id
            AND ms.user_id = :userId
            AND ms.deleted = false
        WHERE c.deleted = false
        GROUP BY c.id, c.title, c.is_group
        ORDER BY lastMessageTime DESC NULLS LAST
    """,
        nativeQuery = true
    )
    fun getAllChats(userId: Long): List<ChatsWithNew>

    @Query(
        """
        select c from Chat c
        join ChatUser cu1 on cu1.chat.id = c.id
        join ChatUser cu2 on cu2.chat.id = c.id
        where c.isGroup = false
          and cu1.user.id = :userId1
          and cu2.user.id = :userId2
          and c.deleted = false
        """
    )
    fun findDirectChat(
        @Param("userId1") userId1: Long,
        @Param("userId2") userId2: Long
    ): Chat?
}

@Repository
interface ChatUserRepository : BaseRepository<ChatUser> {
    fun findAllByChatIdAndDeletedFalse(chatId: Long): List<ChatUser>
    fun findAllByUserIdAndDeletedFalse(userId: Long): List<ChatUser>
    fun existsByChatIdAndUserIdAndDeletedFalse(chatId: Long, userId: Long): Boolean
    fun findByChatIdAndUserIdAndDeletedFalse(chatId: Long, userId: Long): ChatUser?

    @Query("select cu.user from ChatUser cu where cu.chat.id = :chatId and cu.deleted = false")
    fun findUsersByChatId(@Param("chatId") chatId: Long): List<User>
}

@Repository
interface MessageRepository : BaseRepository<Message> {
    fun findAllByChatIdAndDeletedFalse(chatId: Long): List<Message>
    fun findAllBySenderIdAndDeletedFalse(senderId: Long): List<Message>
    fun findAllByReplyToIdAndDeletedFalse(replyToId: Long): List<Message>
    fun findAllByChatIdAndDeletedFalse(chatId: Long, pageable: Pageable): Page<Message>

    fun findTopByChatIdOrderByIdDesc(chatId: Long): Message?

    fun findByChatIdAndIdLessThanOrderByIdDesc(
        chatId: Long,
        id: Long,
        pageable: Pageable
    ): List<Message>
}

@Repository
interface AttachmentRepository : BaseRepository<Attachment> {
    fun findAllByMessageIdAndDeletedFalse(messageId: Long): List<Attachment>
    fun existsByUrlAndDeletedFalse(url: String): Boolean
}

@Repository
interface MessageStatusRepository : BaseRepository<MessageStatus> {
    @Query(
        "select ms from MessageStatus ms " +
                "where ms.user.id = :userId and ms.message.chat.id = :chatId and ms.deleted = false"
    )
    fun findAllByUserIdAndMessageChatId(
        @Param("userId") userId: Long,
        @Param("chatId") chatId: Long
    ): List<MessageStatus>
}
