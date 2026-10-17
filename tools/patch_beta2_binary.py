#!/usr/bin/env python3
from __future__ import annotations
import argparse, struct
from pathlib import Path
UTF8_FLAG=0x100
NO_INDEX=0xffffffff

def u16(b,o): return struct.unpack_from('<H',b,o)[0]
def u32(b,o): return struct.unpack_from('<I',b,o)[0]
def p16(b,o,v): struct.pack_into('<H',b,o,v)
def p32(b,o,v): struct.pack_into('<I',b,o,v)

def dec8len(b,o):
    x=b[o]; o+=1
    if x&0x80: return ((x&0x7f)<<8)|b[o], o+1
    return x,o

def enc8len(n):
    if n>0x7fff: raise ValueError('UTF-8 length too large')
    return bytes([(n>>8)|0x80,n&0xff]) if n>0x7f else bytes([n])

def dec16len(b,o):
    x=u16(b,o); o+=2
    if x&0x8000: return ((x&0x7fff)<<16)|u16(b,o),o+2
    return x,o

def enc16len(n):
    if n>0x7fffffff: raise ValueError('UTF-16 length too large')
    return struct.pack('<HH',(n>>16)|0x8000,n&0xffff) if n>0x7fff else struct.pack('<H',n)

def parse_pool(data:bytes, off:int):
    typ,hs,size=struct.unpack_from('<HHI',data,off)
    if typ!=1 or hs<28: raise ValueError('not string pool')
    count,style_count,flags,strings_start,styles_start=struct.unpack_from('<IIIII',data,off+8)
    out=[]
    for i in range(count):
        p=off+strings_start+u32(data,off+hs+i*4)
        if flags&UTF8_FLAG:
            _,p=dec8len(data,p); bl,p=dec8len(data,p); s=data[p:p+bl].decode('utf-8')
        else:
            ln,p=dec16len(data,p); s=data[p:p+ln*2].decode('utf-16le')
        out.append(s)
    return {'hs':hs,'size':size,'count':count,'style_count':style_count,'flags':flags,'strings_start':strings_start,'styles_start':styles_start,'strings':out}

def rebuild_pool(data:bytes, off:int, replacements:dict[str,str]):
    meta=parse_pool(data,off); strings=[replacements.get(s,s) for s in meta['strings']]
    encoded=[]; offsets=[]; cur=0; utf8=bool(meta['flags']&UTF8_FLAG)
    for s in strings:
        offsets.append(cur)
        if utf8:
            raw=s.encode('utf-8'); units=len(s.encode('utf-16le'))//2
            blob=enc8len(units)+enc8len(len(raw))+raw+b'\0'
        else:
            raw=s.encode('utf-16le'); blob=enc16len(len(raw)//2)+raw+b'\0\0'
        encoded.append(blob); cur+=len(blob)
    string_blob=b''.join(encoded)
    pad=(-len(string_blob))%4; string_blob+=b'\0'*pad
    old_end=off+meta['size']; old_styles_abs=off+meta['styles_start'] if meta['styles_start'] else old_end
    styles=data[old_styles_abs:old_end] if meta['styles_start'] else b''
    prefix=bytearray(data[off:off+meta['strings_start']])
    for i,v in enumerate(offsets): p32(prefix,meta['hs']+i*4,v)
    new_styles_start=meta['strings_start']+len(string_blob) if meta['styles_start'] else 0
    p32(prefix,24,new_styles_start)
    new_chunk=prefix+string_blob+styles
    p32(new_chunk,4,len(new_chunk))
    return data[:off]+bytes(new_chunk)+data[old_end:], strings

def patch_manifest(data:bytes, version_code:int, replacements:dict[str,str]):
    typ,hs,total=struct.unpack_from('<HHI',data,0)
    if typ!=3 or total!=len(data): raise ValueError('not binary XML')
    pool_off=hs
    old=parse_pool(data,pool_off)
    data,strings=rebuild_pool(data,pool_off,replacements)
    newpool=parse_pool(data,pool_off)
    delta=newpool['size']-old['size']
    # root XML size changes with pool
    b=bytearray(data); p32(b,4,len(b)); data=bytes(b)
    o=hs+newpool['size']; patched=False
    while o+8<=len(data):
        ctyp,chs,csz=struct.unpack_from('<HHI',data,o)
        if csz<chs or o+csz>len(data): raise ValueError('bad XML chunk')
        if ctyp==0x0102:
            ext=o+chs; name_idx=u32(data,ext+4)
            if name_idx<len(strings) and strings[name_idx]=='manifest':
                attr_start=u16(data,ext+8); attr_size=u16(data,ext+10); count=u16(data,ext+12); base=ext+attr_start
                b=bytearray(data)
                for i in range(count):
                    a=base+i*attr_size; aname=u32(data,a+4)
                    if aname<len(strings) and strings[aname]=='versionCode':
                        if data[a+15]!=0x10: raise ValueError('versionCode not int')
                        p32(b,a+16,version_code); patched=True
                data=bytes(b); break
        o+=csz
    if not patched: raise ValueError('versionCode not patched')
    return data

def patch_resources(data:bytes,replacements:dict[str,str]):
    typ,hs,total=struct.unpack_from('<HHI',data,0)
    if typ!=2 or total!=len(data): raise ValueError('not resources table')
    pool_off=hs; data,_=rebuild_pool(data,pool_off,replacements)
    b=bytearray(data); p32(b,4,len(b)); return bytes(b)

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--manifest-in'); ap.add_argument('--manifest-out'); ap.add_argument('--resources-in'); ap.add_argument('--resources-out'); ap.add_argument('--version-code',type=int,default=41); args=ap.parse_args()
    manifest_repl={'0.4.0 Beta 1':'0.4.0 Beta 2','ChatGPT 网页版（Beta 1）':'ChatGPT Web'}
    res_repl={'ChatGPT Web Gecko Probe':'ChatGPT Web','ChatGPT 网页版':'ChatGPT Web'}
    if args.manifest_in:
        out=patch_manifest(Path(args.manifest_in).read_bytes(),args.version_code,manifest_repl); Path(args.manifest_out).write_bytes(out)
    if args.resources_in:
        out=patch_resources(Path(args.resources_in).read_bytes(),res_repl); Path(args.resources_out).write_bytes(out)
if __name__=='__main__': main()
