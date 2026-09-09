#!/usr/bin/env python3
"""Author smooth V1 SVG paths from the original pup.png silhouette.

Existing roster and themes only. Clothing is drawn between body and paws.
All elements use the flat path subset supported by the Android art compiler.
"""
from pathlib import Path
import xml.etree.ElementTree as E

OUT=Path(__file__).resolve().parents[1]/'app/src/main/puppy-svg'
PALETTES={
'classic':('#F5B65F','#D28F3B','#FFCE83','#FFE8BE'),
'golden':('#F3B72E','#CB861C','#FFDA71','#FFF0CA'),
'poodle':('#EDD0A2','#C9A977','#FFF0CF','#FFF0D4'),
'spotty':('#E9CBA0','#C4A172','#FFE5BB','#FFF0D3'),
'midnight':('#34415F','#202B44','#536384','#F4DBB2'),
'cloud':('#A7CAD7','#779EAF','#D4EDF3','#FFF0D4'),
'aurora':('#367C84','#225760','#63ACAD','#F5E1BA'),
'cocoa':('#865039','#5E3527','#AC7556','#F9DEBA'),
'snowball':('#F2F3F4','#C8D6E1','#FFFFFF','#FFF1D8'),
'galaxy':('#624A91','#3D2E64','#8971B6','#F5DDBA'),
'neon_buddy':('#253C56','#192B40','#436482','#F8DFB6'),
'golden_night':('#303C54','#202A3E','#50617C','#F8DFAD'),
'halloween':('#A56535','#754225','#C58A4B','#FFE3BA'),
'santa':('#E7C691','#C1A16C','#FFE1B1','#FFF0D4'),
'birthday':('#EFD3A3','#CCAC79','#FFE8BD','#FFF2D9'),
'dev_pup':('#4C453F','#302E2D','#736454','#EED3AD'),
'secret_snoot':('#45404C','#2C2835','#6D6277','#F2D7B4'),
'classic_forever':('#E9AB4C','#BF8133','#FFCE7D','#FFE7BB'),
}

class Art:
 def __init__(self):self.root=E.Element('svg',xmlns='http://www.w3.org/2000/svg',width='256',height='256',viewBox='0 0 512 512')
 def p(self,d,fill='none',stroke=None,w=6):
  a={'d':d,'fill':fill}
  if stroke:a.update({'stroke':stroke,'stroke-width':str(w),'stroke-linecap':'round','stroke-linejoin':'round'})
  E.SubElement(self.root,'path',a)
 def ellipse(self,x,y,rx,ry,c,stroke=None,w=4):
  self.p(f'M{x-rx},{y}a{rx},{ry} 0 1 0 {rx*2},0a{rx},{ry} 0 1 0 {-rx*2},0Z',c,stroke,w)
 def save(self,name):
  E.indent(self.root,space='  ');(OUT/f'v1_{name}.svg').write_bytes(E.tostring(self.root,encoding='utf-8',xml_declaration=True)+b'\n')

def build(name):
 a=Art();p=a.p;el=a.ellipse;fur,shade,light,cream=PALETTES[name];ink='#302116' if name not in ['midnight','galaxy','neon_buddy','golden_night','secret_snoot'] else '#231D30'
 # Tail and haunch establish the original sitting pose.
 p('M381 351 C397 332 400 312 397 296 C393 275 414 287 422 306 C435 334 425 374 395 388Z',fur,ink,8)
 p('M402 301 C410 309 417 327 415 344 C412 357 406 363 402 369 C414 340 406 324 402 301Z',light)
 p('M189 283 C174 302 172 341 174 392 C151 398 145 412 152 425 C157 437 175 437 184 433 L246 431 C275 448 301 445 315 442 C335 451 380 441 389 421 C412 376 367 300 345 281Z',fur,ink,8)
 p('M340 306 C368 323 386 362 388 394 C390 420 366 433 336 429 C365 415 372 393 367 372 C363 349 353 325 340 306Z',shade)
 p('M187 307 C180 331 181 365 184 392 L198 396 C193 355 194 331 201 314Z',light)
 p('M204 296 C200 335 222 365 253 387 C263 363 279 335 285 301 C259 306 227 306 204 296Z',cream)
 p('M207 302 C208 318 213 331 221 341 C245 344 263 336 275 316 L280 302Z','#FFF3DC')
 # Capes wrap the shoulders and sit behind the forelegs, rather than on top of them.
 if name in ['secret_snoot','classic_forever']:
  coat='#46306E' if name=='secret_snoot' else '#A92F43';deep='#2B1C49' if name=='secret_snoot' else '#762039';edge='#B99DE9' if name=='secret_snoot' else '#FFEBCB'
  p('M185 296 C169 323 167 369 173 410 Q198 417 218 404 L253 312 L286 312 L318 405 Q351 420 377 404 C375 356 358 315 344 295Z',coat,ink,5)
  p('M180 328 Q176 369 180 402 L198 398 L210 329Z',deep)
  p('M346 319 Q366 359 368 402 L351 407 Q345 362 328 334Z',deep)
  p('M188 302 Q258 335 341 301','none',edge,10)
  p('M195 317 Q209 328 222 339 M332 316 Q319 328 305 340','none',edge,2)
 # Tailoring for Santa, developer and pumpkin variants.
 if name in ['santa','dev_pup','halloween']:
  coat={'santa':'#BF3041','dev_pup':'#303844','halloween':'#ED8B25'}[name]
  deep={'santa':'#872034','dev_pup':'#1D2631','halloween':'#C45C1D'}[name]
  trim={'santa':'#FFF5E3','dev_pup':'#6B849A','halloween':'#4B354B'}[name]
  p('M189 298 Q220 289 253 307 Q296 290 344 298 C350 326 345 373 329 406 Q300 414 278 407 L202 407 Q183 360 189 298Z',coat,ink,5)
  p('M198 314 C190 340 197 381 207 402 L221 404 Q209 355 218 319Z',deep)
  p('M327 315 Q346 349 326 403 L310 408 Q329 360 316 325Z',deep)
  p('M217 303 Q258 330 321 302','none',trim,12 if name=='santa' else 5)
  if name=='santa':
   p('M263 319 Q258 360 258 405','none',trim,11)
   p('M202 365 Q265 379 333 363 L330 378 Q265 394 205 380Z','#332B2B')
   p('M252 371 L275 371 L275 385 L252 385Z','#EDC766','#6D4A25',2)
   p('M258 375 L269 375 L269 381 L258 381Z','#332B2B')
   el(264,336,3,3,'#E3BA65');el(261,351,3,3,'#E3BA65')
  elif name=='dev_pup':
   p('M231 380 Q253 371 280 380 L283 394 Q256 402 229 393Z',deep,trim,2)
   p('M244 337 L234 344 L244 351 M274 337 L284 344 L274 351 M264 335 L255 354','none','#DBEAF0',3)
   p('M227 316 L229 333 M302 316 L299 333','none','#B4C6D3',2)
  else:
   p('M240 336 L229 350 L249 350Z','#392638');p('M281 336 L271 350 L290 350Z','#392638')
   p('M235 364 Q258 386 285 362 Q258 370 235 364Z','#392638')
   p('M253 352 L261 342 L267 354Z','#392638')
 # Front limbs and feet are always in front of clothing.
 p('M212 354 C221 371 223 390 218 416 C214 438 197 446 177 441 C158 439 165 416 174 404','none',ink,8)
 p('M265 361 C262 391 250 402 247 421 C240 442 262 450 282 444 C310 438 322 398 329 346','none',ink,8)
 p('M177 399 Q195 403 216 405 L212 423 Q204 440 179 433 Q162 430 177 399Z',cream)
 p('M257 405 Q281 397 305 408 Q299 433 280 439 Q250 445 248 427Z',cream)
 p('M324 405 C337 398 357 404 368 415 C380 433 350 444 322 437 C302 432 309 414 324 405Z',cream,ink,6)
 p('M330 349 C345 340 359 342 365 347 M329 350 C318 371 326 390 337 400','none',ink,6)
 p('M181 421 Q176 433 180 439 M196 424 Q194 434 200 440 M259 427 Q254 438 261 445 M278 428 Q274 439 279 444 M324 421 Q319 430 324 438 M340 421 Q335 431 341 440','none',ink,5)
 # Hood sits behind the head and ears.
 if name=='secret_snoot':
  p('M85 252 Q72 161 126 98 Q180 45 255 42 Q332 44 385 102 Q437 164 425 253 L393 284 Q386 212 344 167 L161 166 Q115 218 111 285Z','#3D2A60',ink,7)
  p('M102 247 Q95 164 143 111 Q184 66 252 56 Q326 64 371 113 Q414 163 410 245','none','#8870B2',4)
 # Main head: smooth Bezier curves following original pup.png, no bitmap trace.
 p('M183 86 C222 56 286 54 326 84 C339 65 365 88 402 113 C433 132 419 169 405 195 C392 221 379 211 373 201 C376 217 380 231 374 247 C362 273 328 294 288 301 C256 309 222 301 196 291 C165 280 129 254 134 228 L139 203 C126 221 113 210 102 194 C82 164 78 139 99 119 C127 95 159 78 170 78 C176 78 180 82 183 86Z',fur,ink,8)
 # Soft sculpted shading kept within silhouette.
 p('M157 110 C148 130 148 157 141 181 C135 199 130 210 120 204 C137 220 144 194 150 173Z',shade)
 p('M342 111 C356 135 355 166 368 196 C377 213 386 218 400 202 C380 219 369 175 363 150Z',shade)
 p('M186 91 C222 65 280 62 321 89 C282 71 226 70 190 96Z',light)
 p('M99 130 C116 112 150 90 167 87 C134 105 112 122 98 143 C94 164 111 191 119 197 C100 190 83 151 99 130Z',light)
 p('M342 86 C361 92 391 111 407 126 C419 139 410 167 402 181 C413 151 412 139 400 129 C381 113 361 102 342 86Z',light)
 p('M140 236 C150 218 179 247 209 220 C221 208 231 201 250 201 C271 200 280 207 295 220 C322 247 347 228 356 244 C360 259 324 281 296 291 C261 302 224 296 199 286 C177 277 145 259 140 246Z',cream)
 p('M151 241 C169 241 190 249 213 228 C229 212 242 206 254 207 C275 208 290 227 307 235 C325 244 342 239 350 245 C327 240 319 251 296 239 C273 227 268 216 250 215 C230 214 223 233 205 243 C183 257 164 247 151 241Z','#FFF4DE')
 # Marks remain character-specific and inside the head silhouette.
 if name=='spotty':
  el(138,128,24,31,'#805135');el(349,120,22,26,'#805135');el(341,387,17,18,'#805135')
 if name in ['cocoa','golden_night']:
  el(194,172,10,5,light);el(303,172,10,5,light)
 if name=='poodle':
  for x,y,r in [(119,142,13),(127,122,14),(145,111,12),(368,135,13),(363,115,13),(388,374,15)]:el(x,y,r,r,cream,shade,2)
 # Ears, eyebrows, eyes, nose and smile keep the original pup expression.
 p('M168 109 C151 133 154 164 140 201 M340 110 C355 130 354 171 370 201','none',ink,7)
 p('M187 162 Q196 154 205 160 M299 159 Q308 154 316 161','none',ink,4)
 el(198,201,15,20,ink);el(306,201,15,20,ink)
 el(200,192,4,4,'#FFFFFF');el(304,192,4,4,'#FFFFFF')
 p('M234 219 C244 211 261 212 269 219 C276 228 267 242 251 245 C236 242 225 228 234 219Z',ink)
 p('M240 222 Q251 217 262 223 Q251 226 240 224Z','#8C5C30')
 p('M251 244 C252 265 278 275 286 252 M251 245 C252 266 225 276 216 252','none',ink,6)
 # Accessories fitted to anatomical landmarks, not giant shapes over the face.
 if name in ['golden','cocoa','midnight']:
  c={'golden':'#B23238','cocoa':'#3A8174','midnight':'#7161AB'}[name]
  p('M197 297 Q255 323 322 296','none',ink,10);p('M197 297 Q255 323 322 296','none',c,6)
  el(258,312,8,9,'#EAC36A',ink,2);el(258,310,2,2,'#FFF0BA')
 if name=='aurora':
  p('M191 290 Q252 319 331 289 L328 301 Q255 331 194 303Z','#74DCCB',ink,3)
  p('M200 302 Q261 326 322 301 L318 308 Q260 333 203 310Z','#B48ADF')
  p('M305 312 L324 308 L330 348 L316 342 L307 347Z','#B48ADF',ink,3)
 if name in ['cloud','snowball','golden_night']:
  col={'cloud':'#80B9CC','snowball':'#62ACC8','golden_night':'#35486B'}[name]
  p('M203 300 Q256 322 321 299 L270 350 Q261 357 253 349Z',col,ink,4)
  p('M217 310 L261 342 L306 310','none','#DAEBEB' if name!='golden_night' else '#E6BD69',2)
  if name=='cloud':
   p('M251 326 C244 316 254 311 260 316 C265 306 278 312 276 319 C285 318 287 330 280 331 L253 331Z','#F5FBFE')
  if name=='golden_night':p('M273 316 C253 312 250 338 273 338 C260 332 262 322 273 316Z','#F1C463')
  if name=='snowball':el(261,326,5,5,'#EAFBFF',ink,1)
 if name in ['midnight','galaxy']:
  if name=='midnight':p('M271 88 C243 79 235 121 264 126 C250 115 252 96 271 88Z','#F3D485')
  for x,y,r in [(218,107,3),(299,139,2),(314,104,3),(164,232,2)]:
   p(f'M{x-r} {y} L{x} {y-r} L{x+r} {y} L{x} {y+r}Z','#A9E3D8' if name=='galaxy' else '#F3D485')
 if name in ['dev_pup','neon_buddy']:
  c='#28D4ED' if name=='neon_buddy' else '#8B99AB'
  # Glasses rest on the forehead so both eyes remain fully visible.
  p('M162 130 Q189 123 218 131 L216 152 Q190 157 166 150Z','#23303C',c,3)
  p('M278 131 Q307 123 335 132 L331 151 Q305 157 280 152Z','#23303C',c,3)
  p('M218 138 Q248 128 278 138 M161 136 L150 137 M334 138 L345 140','none',c,3)
  p('M171 132 L198 129 L178 148Z','#456276');p('M287 133 L314 129 L295 148Z','#456276')
  if name=='neon_buddy':
   p('M211 310 Q255 337 312 310','none','#23D3DF',5)
   el(214,316,10,12,'#182B40','#23D3DF',3);el(310,316,10,12,'#182B40','#23D3DF',3)
 if name=='poodle':
  p('M239 96 L217 88 L218 107 L240 103 M250 96 L271 88 L272 107 L250 103','#E989B4',ink,2);el(245,99,6,6,'#F7B0CE',ink,2)
 if name=='santa':
  p('M171 90 C186 62 216 28 262 33 C302 34 315 47 337 70 L327 83 C305 68 294 65 282 70 L298 97Z','#BF3041',ink,5)
  p('M190 78 Q225 46 256 43 Q224 63 217 87Z','#E55359')
  p('M170 86 Q235 73 300 87 Q307 95 300 104 Q235 92 174 103 Q162 97 170 86Z','#FFF3DB',ink,4)
  el(335,75,13,14,'#FFF3DB',ink,4)
  p('M178 99 Q234 90 293 99','none','#DBCDB8',2)
 if name=='birthday':
  p('M220 95 L252 38 Q255 34 258 40 L282 95Z','#9BD9D1',ink,4)
  p('M232 72 L266 61 L272 75 L226 89Z','#F5D7A5');el(254,37,7,8,'#E68FB3',ink,2)
  p('M216 96 Q252 103 285 96','none','#F4A3BD',6)
  p('M211 303 Q256 324 316 302 L295 326 L257 322 L232 328Z','#E890B0',ink,3)
  el(257,317,6,6,'#F9DA83',ink,2)
 if name=='halloween':
  p('M176 87 Q202 57 250 60 Q291 57 326 87 L327 95 Q249 75 174 96Z','#E98826',ink,4)
  p('M245 62 Q243 47 253 43 L262 46 L257 63Z','#548257',ink,3)
  p('M200 77 Q218 63 241 65 M267 65 Q289 66 307 78','none','#FFB352',3)
 if name=='classic_forever':
  p('M218 85 L210 55 L234 65 L250 39 L267 65 L291 52 L283 87Z','#E7B85B',ink,4)
  p('M220 79 L280 79','none','#FFF1BF',3);el(251,66,4,6,'#B73650',ink,1)
  el(258,316,8,9,'#E7B85B',ink,3)
 if name=='secret_snoot':
  p('M148 114 Q188 67 252 62 Q317 67 358 116','none','#B99DE9',4)
  el(258,316,7,8,'#D7B75E',ink,2)
 a.save(name)

if __name__=='__main__':
 for name in PALETTES:build(name)
 print('Rebuilt 18 existing V1 characters with fitted outfits and smooth paths.')
