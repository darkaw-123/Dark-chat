'use strict';
(function(){
 if(window.top!==window){try{if(window.top.DreamAds){window.DreamAds=window.top.DreamAds;return}}catch(_){}}
 let pending=null;
 function fail(message){let p=pending;pending=null;if(p&&p.fail)p.fail(message||'The ad was not completed. Please try again.');}
 window.DreamAds={
  request(reason,success,onFail){if(pending){if(onFail)onFail('An ad is already in progress.');return}if(!window.AndroidAds||typeof AndroidAds.showRewarded!=='function'){if(onFail)onFail('Rewarded ads are available in the Android app.');return}pending={success,fail:onFail};try{AndroidAds.showRewarded(reason==='item'?'item':'coins')}catch(_){fail('Ad is not available right now.')}},
  _rewarded(){let p=pending;pending=null;if(p&&p.success)p.success()},
  _failed(){fail('The ad was skipped or unavailable. No reward was granted.')},
  privacy(){if(window.AndroidAds&&typeof AndroidAds.showPrivacyOptions==='function')AndroidAds.showPrivacyOptions()}
 };
 function syncPremium(){try{if(window.AndroidAds&&window.DreamAuth)AndroidAds.setPremium(!!DreamAuth.premium)}catch(_){}}
 if(window.DreamAuth&&DreamAuth.ready)DreamAuth.ready.then(syncPremium);window.addEventListener('dream-auth',syncPremium);
})();
