/** The whole site lives in the `kik` Worker; this Pages project only gives it a shorter address. */
export const onRequest=({request,env})=>env.KIK.fetch(request);
