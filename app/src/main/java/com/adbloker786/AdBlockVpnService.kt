package com.adbloker786

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.content.Intent
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class AdBlockVpnService:VpnService(){ private var tun:ParcelFileDescriptor?=null; @Volatile private var running=false
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int { if(running)return START_STICKY; val b=Builder().setSession("Adbloker 786").addAddress("10.77.0.1",32).addAddress("fd77:786::1",128).addRoute("0.0.0.0",0).addRoute("::",0).addDnsServer("10.77.0.1").setMtu(1500); tun=b.establish(); if(tun==null){stopSelf();return START_NOT_STICKY}; running=true; thread(name="dns-vpn"){ pump() }; return START_STICKY }
 private fun pump(){ val fd=tun?:return; val input=FileInputStream(fd.fileDescriptor); val output=FileOutputStream(fd.fileDescriptor); val buf=ByteArray(32767); while(running){ try { val n=input.read(buf); if(n<=0)continue; val p=IpDns.parse(buf,n)?:continue; val name=p.second; val blocked=name.endsWith(".doubleclick.net")||name.endsWith(".googlesyndication.com")||name.endsWith(".googleadservices.com")||name.endsWith(".adnxs.com")||name.endsWith(".facebook.net")||name.endsWith(".adsrvr.org"); val response=if(blocked) IpDns.nxdomain(p.first) else forward(p.first); if(response!=null)output.write(response) }catch(_:Exception){ if(!running)break } } }
 private fun forward(packet:ByteArray):ByteArray? { val q=IpDns.parse(packet,packet.size)?.first?:return null; return try { DatagramSocket().use{s->s.soTimeout=2500; val target=InetAddress.getByName("1.1.1.1"); val out=DatagramPacket(q,q.size,target,53); s.send(out); val data=ByteArray(4096); val res=DatagramPacket(data,data.size); s.receive(res); IpDns.wrap(packet,data.copyOf(res.length)) } }catch(_:Exception){null} }
 override fun onDestroy(){running=false;tun?.close();tun=null;super.onDestroy()}
}
private object IpDns { fun parse(b:ByteArray,n:Int):Pair<ByteArray,String>? { if(n<28)return null; val v=(b[0].toInt() ushr 4) and 15; val off=if(v==4){val ihl=(b[0].toInt() and 15)*4;if(n<ihl+8||b[9].toInt()!=17)return null;ihl+8}else if(v==6){if(n<48||b[6].toInt()!=17)return null;48}else return null; val port=((b[off+2].toInt() and 255) shl 8) or (b[off+3].toInt() and 255); if(port!=53)return null; val start=off+8; val dns=b.copyOfRange(start,n); if(dns.size<17)return null; var i=12; val labels=mutableListOf<String>(); while(i<dns.size){val l=dns[i++].toInt() and 255;if(l==0)break;if(l>63||i+l>dns.size)return null;labels+=String(dns,i,l,Charsets.US_ASCII);i+=l};return dns to labels.joinToString(".").lowercase() }
 fun nxdomain(q:ByteArray):ByteArray { val r=q.copyOf();r[2]=0x81.toByte();r[3]=0x83.toByte();r[6]=0;r[7]=0;r[8]=0;r[9]=0;return r }
 fun wrap(original:ByteArray,dns:ByteArray):ByteArray? { val v=(original[0].toInt() ushr 4) and 15; val off=if(v==4)28 else 48; val total=off+dns.size; val r=ByteArray(total); System.arraycopy(original,0,r,0,off); if(v==4){ for(i in 0..3){r[12+i]=original[16+i];r[16+i]=original[12+i]};r[2]=(total shr 8).toByte();r[3]=total.toByte();r[10]=0;r[11]=0; var sum=0;for(i in 0 until 20 step 2){sum+=((r[i].toInt() and 255) shl 8)+(r[i+1].toInt() and 255)};while(sum shr 16!=0)sum=(sum and 65535)+(sum shr 16);val c=sum.inv();r[10]=(c shr 8).toByte();r[11]=c.toByte() } else { for(i in 0..15){r[8+i]=original[24+i];r[24+i]=original[8+i]};r[4]=(8+dns.size shr 8).toByte();r[5]=(8+dns.size).toByte() };r[off-8]=original[off-6];r[off-7]=original[off-5];r[off-6]=original[off-8];r[off-5]=original[off-7];r[off-4]=0;r[off-3]=0;r[off-2]=0;r[off-1]=0;System.arraycopy(dns,0,r,off,dns.size);return r }
}
