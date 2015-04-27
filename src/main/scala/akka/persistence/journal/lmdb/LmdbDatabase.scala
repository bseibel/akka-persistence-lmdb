package akka.persistence.journal.lmdb

import java.io.File

import akka.persistence.journal.lmdb.LmdbDatabase.TxType
import org.fusesource.lmdbjni._

import scala.collection.concurrent.TrieMap

object LmdbDatabase {

  object TxType extends Enumeration {
    val WRITE = Value(0)
    val READ = Value(1)
  }

}

trait LmdbDatabase {
  this: LmdbJournal =>

  val env = initEnvironment()
  val openedDbMap: TrieMap[String, Database] = TrieMap.empty

  def withTransaction[T](txType: TxType.Value)(func: (Transaction) => T): T = {
    val tx = txType match {
      case TxType.READ => env.createReadTransaction()
      case TxType.WRITE => env.createWriteTransaction()
    }
    try {
      val result = func(tx)
      tx.commit()
      result
    } finally {
      tx.close()
    }
  }

  def withCursor[T](db: Database, tx: Transaction)(func: (BufferCursor) => T): T = {
    func(db.bufferCursor(tx))
  }

  def getDatabase(persistenceId: String): Database = {
    openedDbMap.getOrElseUpdate(persistenceId, env.openDatabase(persistenceId, Constants.CREATE | Constants.INTEGERKEY))
  }

  def initEnvironment(): Env = {
    val config = context.system.settings.config.getConfig("lmdb-journal")

    val directory = new File(config.getString("dir"))
    if (!directory.exists()) directory.mkdirs()

    val env = new Env()
    env.setMaxDbs(config.getLong("maxdbs"))
    env.open(config.getString("dir"))
    env
  }

}
