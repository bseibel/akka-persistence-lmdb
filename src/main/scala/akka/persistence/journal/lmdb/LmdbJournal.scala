package akka.persistence.journal.lmdb

import java.nio.ByteBuffer

import akka.persistence.journal.SyncWriteJournal
import akka.persistence.journal.lmdb.LmdbDatabase.TxType
import akka.persistence.{PersistentConfirmation, PersistentId, PersistentRepr}
import akka.serialization.SerializationExtension

import scala.collection.immutable.Seq
import scala.concurrent.Future

object LmdbJournal {

  object JournalFlags {
    val CONFIRMED: Int = 1
    val DELETED: Int = 2
  }

}

class LmdbJournal extends SyncWriteJournal with LmdbDatabase {

  val serialization = SerializationExtension(context.system)

  private[this] val replayDispatcher = context.system.dispatchers.lookup(
    context.system.settings.config.getString("lmdb-journal.replay-dispatcher")
  )

  override def writeMessages(messages: Seq[PersistentRepr]): Unit = {
    // Pre open all of the db's we will need for this write tx.
    val groups = messages.groupBy(_.persistenceId)
    val openDbs = groups.keySet.map(db => db -> getDatabase(db)).toMap

    withTransaction(TxType.WRITE) { tx =>
      groups.foreach {
        case (persistenceId, values) =>
          withCursor(openDbs(persistenceId), tx) { cursor =>
            cursor.first()
            for (m <- messages) {
              val payload = serialization.serialize(m).get
              cursor.keyWriteLong(m.sequenceNr)
              cursor.valWriteBytes(payload)
              cursor.overwrite()
            }
          }
      }
    }
  }

  override def deleteMessagesTo(persistenceId: String, toSequenceNr: Long, permanent: Boolean): Unit = {
    val db = getDatabase(persistenceId)
    withTransaction(TxType.WRITE) { tx =>
      withCursor(db, tx) { cursor =>
        if (cursor.seek(longToByteArray(toSequenceNr))) {
          do {
            if (permanent) cursor.delete()
            else {
              val oldMsg = serialization.deserialize(cursor.valBytes(), classOf[PersistentRepr]).get
              val newBytes = serialization.serialize(oldMsg.update(deleted = true)).get
              cursor.valWriteBytes(newBytes)
              cursor.overwrite()
            }
          } while (cursor.prev())
        }
      }
    }
  }

  @deprecated("deleteMessages will be removed.")
  override def deleteMessages(messageIds: Seq[PersistentId], permanent: Boolean): Unit = {
    val groups = messageIds.groupBy(_.persistenceId)
    val openDbs = groups.keySet.map(db => db -> getDatabase(db)).toMap
    withTransaction(TxType.WRITE) { tx =>
      groups.foreach {
        case (persistenceId, messages) =>
          withCursor(openDbs(persistenceId), tx) { cursor =>
            cursor.first()
            for (m <- messages) {
              if (cursor.seek(longToByteArray(m.sequenceNr))) {
                if (byteArrayToLong(cursor.keyBytes()) == m.sequenceNr) {
                  if (permanent) cursor.delete()
                  else {
                    val oldMsg = serialization.deserialize(cursor.valBytes(), classOf[PersistentRepr]).get
                    val newBytes = serialization.serialize(oldMsg.update(deleted = true)).get
                    cursor.valWriteBytes(newBytes)
                    cursor.overwrite()
                  }
                }
              }
            }
          }
      }
    }
  }

  @deprecated("writeConfirmations will be removed, since Channels will be removed.")
  override def writeConfirmations(confirmations: Seq[PersistentConfirmation]): Unit = {
    val groups = confirmations.groupBy(_.persistenceId)
    val openDbs = groups.keySet.map(db => db -> getDatabase(db)).toMap
    withTransaction(TxType.WRITE) { tx =>
      groups.foreach {
        case (persistenceId, confs) =>
          withCursor(openDbs(persistenceId), tx) { cursor =>
            val cGrouped = confs.groupBy(_.sequenceNr)
            cGrouped.foreach {
              case (seqNo, confirms) =>
                if (cursor.seek(longToByteArray(seqNo))) {
                  if (byteArrayToLong(cursor.keyBytes()) == seqNo) {
                    // Since writeConfirmations is going away, we won't waste effort trying to make this efficient.
                    val oldMsg = serialization.deserialize(cursor.valBytes(), classOf[PersistentRepr]).get
                    val newBytes = serialization.serialize(oldMsg.update(confirms = confirms.map(_.channelId))).get
                    cursor.keyWriteLong(seqNo)
                    cursor.valWriteBytes(newBytes)
                  }
                }
            }
            cursor.overwrite()
          }
      }

    }
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    val db = getDatabase(persistenceId)
    Future {
      withTransaction(TxType.READ) { tx =>
        withCursor(db, tx) { cursor =>
          if (cursor.last()) {
            val key = byteArrayToLong(cursor.keyBytes())
            key
          } else 0L
        }
      }
    }(replayDispatcher)
  }

  override def asyncReplayMessages(persistenceId: String,
                                   fromSequenceNr: Long,
                                   toSequenceNr: Long,
                                   max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
    val db = getDatabase(persistenceId)

    Future {
      withTransaction(TxType.READ) { tx =>
        withCursor(db, tx) { cursor =>
          if (cursor.seek(longToByteArray(fromSequenceNr))) {

            var key = byteArrayToLong(cursor.keyBytes())
            var cont = true
            var count = 0
            while (cont && key <= toSequenceNr && count < max) {

              val payload = cursor.valBytes()

              val message = serialization.deserialize(payload, classOf[PersistentRepr]).get
              replayCallback(message)

              count = count + 1
              if (!cursor.next()) cont = false
              else {
                key = byteArrayToLong(cursor.keyBytes())
              }

            }
          }
        }
      }
    }(replayDispatcher)
  }

  override def postStop(): Unit = {
    openedDbMap.foreach(_._2.close())
    super.postStop()
  }

  private[this] final def longToByteArray(long: Long): Array[Byte] = {
    val buffer = ByteBuffer.allocate(8)
    buffer.putLong(long)
    buffer.array()
  }

  private[this] final def byteArrayToLong(array: Array[Byte]): Long = {
    val buffer = ByteBuffer.wrap(array)
    buffer.getLong
  }

}
