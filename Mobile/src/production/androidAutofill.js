// Autofill belongs to Android's selected credential provider (Google Password
// Manager, Samsung Pass, Bitwarden, etc.). Puppy Clicker never stores passwords
// locally, supplies secrets to this method, or triggers a save on failed login.
export async function commitVerifiedPupCredentials(native,result){
 if(result?.status!=='VERIFIED'||typeof native?.commitPupAutofill!=='function')return false;
 try{return await native.commitPupAutofill()===true;}
 catch{return false;} // Android autofill is optional; never block Pup Account auth.
}
