// Exact geometry rules ported from Kotlin PuppyBlackjackLayout.kt.
export const BLACKJACK_SOURCE_WIDTH=1122;
export const BLACKJACK_SOURCE_HEIGHT=1402;
export const BLACKJACK_ART_TOP=86;
export const BLACKJACK_ART_HEIGHT=1188;
export const BLACKJACK_TABLE_ASPECT=BLACKJACK_SOURCE_WIDTH/BLACKJACK_ART_HEIGHT;

export function blackjackTable(width){
 if(!Number.isFinite(width)||width<=0)throw new RangeError('Invalid table width');
 const height=width/BLACKJACK_TABLE_ASPECT;
 const artworkHeight=width*BLACKJACK_SOURCE_HEIGHT/BLACKJACK_SOURCE_WIDTH;
 const artworkTop=width*BLACKJACK_ART_TOP/BLACKJACK_SOURCE_WIDTH;
 const cardWidth=Math.min(width*.13,56);
 return {
  width,height,artworkHeight,artworkTop,cardWidth,
  dealer:{left:width*.25,top:artworkHeight*.245-artworkTop,width:width*.48,height:artworkHeight*.15},
  player:{left:width*.18,top:artworkHeight*.585-artworkTop,width:width*.64,height:artworkHeight*.20}
 };
}
export function blackjackFan(available,preferredCardWidth,count){
 if(!Number.isFinite(available)||available<=0||!Number.isFinite(preferredCardWidth)||preferredCardWidth<=0||
    !Number.isSafeInteger(count)||count<0||count>52)throw new RangeError('Invalid card fan geometry');
 if(count===0)return {cardWidth:preferredCardWidth,step:0,width:0};
 const cardWidth=Math.min(preferredCardWidth,available/(1+(count-1)*.43));
 const step=count===1?0:Math.min(cardWidth+6,(available-cardWidth)/(count-1));
 return {cardWidth,step,width:cardWidth+step*(count-1)};
}
export function blackjackSharedCardWidth(metrics,dealerCount,playerCount){
 return Math.min(metrics.cardWidth,
  blackjackFan(metrics.dealer.width,metrics.cardWidth,dealerCount).cardWidth,
  blackjackFan(metrics.player.width,metrics.cardWidth,playerCount).cardWidth);
}
