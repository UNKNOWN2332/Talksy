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
import uz.shukrullaev.com.talksy.*


/**
 * @see uz.shukrullaev.com.talksy
 * @author Abdulloh
 * @since 18/08/2025 6:08 pm
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

class BaseRepositoryImpl<T : BaseEntity>(
    entityInformation: JpaEntityInformation<T, Long>, entityManager: EntityManager,
) : SimpleJpaRepository<T, Long>(entityInformation, entityManager), BaseRepository<T> {

    val isNotDeletedSpecification = Specification<T> { root, _, cb -> cb.equal(root.get<Boolean>("deleted"), false) }

    override fun findByIdAndDeletedFalse(id: Long) = findByIdOrNull(id)?.run { if (deleted) null else this }
    override fun existsByIdAndDeletedFalse(id: Long) = findByIdOrNull(id)?.run { !deleted }

    @Transactional
    override fun trash(id: Long): T? =
        findByIdOrNull(id)?.takeIf { !it.deleted }?.run {
            deleted = true
            save(this)
        }


    override fun findAllNotDeleted(): List<T> = findAll(isNotDeletedSpecification)
    override fun findAllNotDeleted(pageable: Pageable): Page<T> = findAll(isNotDeletedSpecification, pageable)
    override fun trashList(ids: List<Long>): List<T?> = ids.map { trash(it) }

}


@Repository
interface UserRepository : BaseRepository<User> {

    fun findAllByIdInAndDeletedFalse(ids: Set<Long>): List<User>

    fun findByTelegramIdAndDeletedFalse(telegramId: String): User?

    fun findByUsername(username: String): User?

    fun findByUsernameAndDeletedFalse(username: String): User?
}

@Repository
interface ChatRepository : BaseRepository<Chat> {
    fun findAllByIsGroupAndDeletedFalse(isGroup: Boolean): List<Chat>
}

@Repository
interface MessageRepository : BaseRepository<Message> {
    fun findAllByChatIdAndDeletedFalse(chatId: Long): List<Message>
    fun findAllBySenderIdAndDeletedFalse(senderId: Long): List<Message>
    fun findAllByReplyToIdAndDeletedFalse(replyToId: Long): List<Message>
}

@Repository
interface ChatUserRepository : BaseRepository<ChatUser> {
    fun findAllByChatIdAndDeletedFalse(chatId: Long): List<ChatUser>
    fun findAllByUserIdAndDeletedFalse(userId: Long): List<ChatUser>
    fun existsByChatIdAndUserIdAndDeletedFalse(chatId: Long, userId: Long): Boolean
    fun findByChatIdAndUserIdAndDeletedFalse(chatId: Long, userId: Long): ChatUser?
}

@Repository
interface AttachmentRepository : BaseRepository<Attachment> {
    fun findAllByMessageIdAndDeletedFalse(messageId: Long): List<Attachment>
    fun existsByUrlAndDeletedFalse(url: String): Boolean
}

