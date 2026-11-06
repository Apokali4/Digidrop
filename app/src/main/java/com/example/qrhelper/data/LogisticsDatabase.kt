package com.example.qrhelper.data
import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
enum class DeliveryStatus{PENDING,IN_PROGRESS,COMPLETED,HAS_DISCREPANCY}
enum class CargoUnitType{PACK,PIECE}
enum class CargoBarcodeType{EAN_13,DATA_MATRIX,QR_CODE,GS1_128,UPC_A,SSCC}
enum class DiscrepancyReason{БРАК,НЕДОВОЗ,ИЗЛИШЕК}
class LogisticsConverters{
 @TypeConverter fun s(v:DeliveryStatus)=v.name
 @TypeConverter fun s(v:String)=DeliveryStatus.valueOf(v)
 @TypeConverter fun u(v:CargoUnitType)=v.name
 @TypeConverter fun u(v:String)=CargoUnitType.valueOf(v)
 @TypeConverter fun b(v:CargoBarcodeType)=v.name
 @TypeConverter fun b(v:String)=CargoBarcodeType.valueOf(v)
 @TypeConverter fun d(v:DiscrepancyReason?):String?=v?.name
 @TypeConverter fun d(v:String?):DiscrepancyReason?=v?.let(DiscrepancyReason::valueOf)
}
@Entity(tableName="delivery_routes") data class DeliveryRouteEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val routeNumber:String,val createdAt:Long=System.currentTimeMillis(),val active:Boolean=true)
@Entity(tableName="delivery_points",foreignKeys=[ForeignKey(entity=DeliveryRouteEntity::class,parentColumns=["id"],childColumns=["routeId"],onDelete=ForeignKey.CASCADE)],indices=[Index("routeId")])
data class DeliveryPointEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val routeId:Long,val storeName:String,val address:String,val phone:String="",val sortOrder:Int=0,val timeSlot:String="",val contactPerson:String="",val contactPhone:String="",val status:DeliveryStatus=DeliveryStatus.PENDING,val signaturePath:String="",val photoDocUri:String="")
@Entity(tableName="cargo_items",foreignKeys=[ForeignKey(entity=DeliveryPointEntity::class,parentColumns=["id"],childColumns=["pointId"],onDelete=ForeignKey.CASCADE)],indices=[Index("pointId"),Index("barcode")])
data class CargoItemEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val pointId:Long,val barcode:String,val itemName:String,val expectedQty:Int,val scannedQty:Int=0,val unitType:CargoUnitType=CargoUnitType.PACK,val barcodeType:CargoBarcodeType=CargoBarcodeType.EAN_13,val discrepancyQty:Int=0,val discrepancyReason:DiscrepancyReason?=null)
data class DiscrepancyReportRow(val pointName:String,val pointAddress:String,val itemName:String,val barcode:String,val expectedQty:Int,val scannedQty:Int,val discrepancyQty:Int,val discrepancyReason:String?)
@Dao interface LogisticsDao{
 @Insert suspend fun insertRoute(v:DeliveryRouteEntity):Long
 @Insert suspend fun insertPoint(v:DeliveryPointEntity):Long
 @Insert suspend fun insertCargo(v:List<CargoItemEntity>)
 @Query("UPDATE delivery_routes SET active=0 WHERE active=1") suspend fun deactivateRoutes()
 @Query("SELECT * FROM delivery_routes WHERE active=1 ORDER BY createdAt DESC LIMIT 1") fun activeRoute():Flow<DeliveryRouteEntity?>
 @Query("SELECT * FROM delivery_points WHERE routeId=:id ORDER BY sortOrder") fun points(id:Long):Flow<List<DeliveryPointEntity>>
 @Query("SELECT * FROM delivery_points WHERE id=:id") fun point(id:Long):Flow<DeliveryPointEntity?>
 @Query("SELECT * FROM cargo_items WHERE pointId=:id ORDER BY itemName") fun cargo(id:Long):Flow<List<CargoItemEntity>>
 @Query("SELECT * FROM cargo_items WHERE pointId=:pointId AND barcode=:barcode LIMIT 1") suspend fun findCargo(pointId:Long,barcode:String):CargoItemEntity?
 @Query("UPDATE cargo_items SET scannedQty=MIN(expectedQty-discrepancyQty,scannedQty+1) WHERE id=:id AND scannedQty+discrepancyQty<expectedQty") suspend fun incrementScanned(id:Long)
 @Query("UPDATE cargo_items SET discrepancyQty=:qty,discrepancyReason=:reason WHERE id=:id AND :qty>=0 AND scannedQty+:qty<=expectedQty") suspend fun updateDiscrepancy(id:Long,qty:Int,reason:DiscrepancyReason)
 @Query("UPDATE delivery_points SET status=:status WHERE id=:id") suspend fun updateStatus(id:Long,status:DeliveryStatus)
 @Query("UPDATE delivery_points SET signaturePath=:path WHERE id=:id") suspend fun saveSignature(id:Long,path:String)
 @Query("UPDATE delivery_points SET photoDocUri=:uri WHERE id=:id") suspend fun savePhoto(id:Long,uri:String)
 @Query("SELECT COALESCE(SUM(expectedQty),0) FROM cargo_items WHERE pointId=:id") fun expected(id:Long):Flow<Int>
 @Query("SELECT COALESCE(SUM(scannedQty),0) FROM cargo_items WHERE pointId=:id") fun scanned(id:Long):Flow<Int>
 @Query("SELECT COALESCE(SUM(discrepancyQty),0) FROM cargo_items WHERE pointId=:id") fun discrepancies(id:Long):Flow<Int>
 @Query("SELECT COUNT(*) FROM cargo_items WHERE pointId=:id AND scannedQty+discrepancyQty<expectedQty") suspend fun unresolved(id:Long):Int
 @Query("SELECT COUNT(*) FROM cargo_items WHERE pointId=:id AND discrepancyQty>0 AND discrepancyReason IS NULL") suspend fun invalidDiscrepancies(id:Long):Int
 @Transaction suspend fun closePoint(id:Long):Boolean{if(unresolved(id)>0||invalidDiscrepancies(id)>0)return false;updateStatus(id,DeliveryStatus.COMPLETED);return true}
 @Query("SELECT p.storeName AS pointName,p.address AS pointAddress,c.itemName AS itemName,c.barcode AS barcode,c.expectedQty AS expectedQty,c.scannedQty AS scannedQty,c.discrepancyQty AS discrepancyQty,c.discrepancyReason AS discrepancyReason FROM cargo_items c JOIN delivery_points p ON p.id=c.pointId WHERE p.routeId=:routeId AND (c.discrepancyQty>0 OR c.scannedQty<c.expectedQty) ORDER BY p.sortOrder,p.storeName,c.itemName") suspend fun discrepancyReport(routeId:Long):List<DiscrepancyReportRow>
}
@Database(entities=[DeliveryRouteEntity::class,DeliveryPointEntity::class,CargoItemEntity::class],version=1,exportSchema=true)
@TypeConverters(LogisticsConverters::class)
abstract class LogisticsDatabase:RoomDatabase(){abstract fun dao():LogisticsDao
 companion object{@Volatile private var db:LogisticsDatabase?=null;fun get(c:Context):LogisticsDatabase=db?:synchronized(this){db?:Room.databaseBuilder(c.applicationContext,LogisticsDatabase::class.java,"digidrop.db").build().also{db=it}}}
}