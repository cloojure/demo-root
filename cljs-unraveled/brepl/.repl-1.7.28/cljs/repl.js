// Compiled by ClojureScript 1.7.28 {}
goog.provide('cljs.repl');
goog.require('cljs.core');
cljs.repl.print_doc = (function cljs$repl$print_doc(m){
cljs.core.println.call(null,"-------------------------");

cljs.core.println.call(null,[cljs.core.str((function (){var temp__4425__auto__ = new cljs.core.Keyword(null,"ns","ns",441598760).cljs$core$IFn$_invoke$arity$1(m);
if(cljs.core.truth_(temp__4425__auto__)){
var ns = temp__4425__auto__;
return [cljs.core.str(ns),cljs.core.str("/")].join('');
} else {
return null;
}
})()),cljs.core.str(new cljs.core.Keyword(null,"name","name",1843675177).cljs$core$IFn$_invoke$arity$1(m))].join(''));

if(cljs.core.truth_(new cljs.core.Keyword(null,"protocol","protocol",652470118).cljs$core$IFn$_invoke$arity$1(m))){
cljs.core.println.call(null,"Protocol");
} else {
}

if(cljs.core.truth_(new cljs.core.Keyword(null,"forms","forms",2045992350).cljs$core$IFn$_invoke$arity$1(m))){
var seq__4557_4571 = cljs.core.seq.call(null,new cljs.core.Keyword(null,"forms","forms",2045992350).cljs$core$IFn$_invoke$arity$1(m));
var chunk__4558_4572 = null;
var count__4559_4573 = (0);
var i__4560_4574 = (0);
while(true){
if((i__4560_4574 < count__4559_4573)){
var f_4575 = cljs.core._nth.call(null,chunk__4558_4572,i__4560_4574);
cljs.core.println.call(null,"  ",f_4575);

var G__4576 = seq__4557_4571;
var G__4577 = chunk__4558_4572;
var G__4578 = count__4559_4573;
var G__4579 = (i__4560_4574 + (1));
seq__4557_4571 = G__4576;
chunk__4558_4572 = G__4577;
count__4559_4573 = G__4578;
i__4560_4574 = G__4579;
continue;
} else {
var temp__4425__auto___4580 = cljs.core.seq.call(null,seq__4557_4571);
if(temp__4425__auto___4580){
var seq__4557_4581__$1 = temp__4425__auto___4580;
if(cljs.core.chunked_seq_QMARK_.call(null,seq__4557_4581__$1)){
var c__3147__auto___4582 = cljs.core.chunk_first.call(null,seq__4557_4581__$1);
var G__4583 = cljs.core.chunk_rest.call(null,seq__4557_4581__$1);
var G__4584 = c__3147__auto___4582;
var G__4585 = cljs.core.count.call(null,c__3147__auto___4582);
var G__4586 = (0);
seq__4557_4571 = G__4583;
chunk__4558_4572 = G__4584;
count__4559_4573 = G__4585;
i__4560_4574 = G__4586;
continue;
} else {
var f_4587 = cljs.core.first.call(null,seq__4557_4581__$1);
cljs.core.println.call(null,"  ",f_4587);

var G__4588 = cljs.core.next.call(null,seq__4557_4581__$1);
var G__4589 = null;
var G__4590 = (0);
var G__4591 = (0);
seq__4557_4571 = G__4588;
chunk__4558_4572 = G__4589;
count__4559_4573 = G__4590;
i__4560_4574 = G__4591;
continue;
}
} else {
}
}
break;
}
} else {
if(cljs.core.truth_(new cljs.core.Keyword(null,"arglists","arglists",1661989754).cljs$core$IFn$_invoke$arity$1(m))){
var arglists_4592 = new cljs.core.Keyword(null,"arglists","arglists",1661989754).cljs$core$IFn$_invoke$arity$1(m);
if(cljs.core.truth_((function (){var or__2779__auto__ = new cljs.core.Keyword(null,"macro","macro",-867863404).cljs$core$IFn$_invoke$arity$1(m);
if(cljs.core.truth_(or__2779__auto__)){
return or__2779__auto__;
} else {
return new cljs.core.Keyword(null,"repl-special-function","repl-special-function",1262603725).cljs$core$IFn$_invoke$arity$1(m);
}
})())){
cljs.core.prn.call(null,arglists_4592);
} else {
cljs.core.prn.call(null,((cljs.core._EQ_.call(null,new cljs.core.Symbol(null,"quote","quote",1377916282,null),cljs.core.first.call(null,arglists_4592)))?cljs.core.second.call(null,arglists_4592):arglists_4592));
}
} else {
}
}

if(cljs.core.truth_(new cljs.core.Keyword(null,"special-form","special-form",-1326536374).cljs$core$IFn$_invoke$arity$1(m))){
cljs.core.println.call(null,"Special Form");

cljs.core.println.call(null," ",new cljs.core.Keyword(null,"doc","doc",1913296891).cljs$core$IFn$_invoke$arity$1(m));

if(cljs.core.contains_QMARK_.call(null,m,new cljs.core.Keyword(null,"url","url",276297046))){
if(cljs.core.truth_(new cljs.core.Keyword(null,"url","url",276297046).cljs$core$IFn$_invoke$arity$1(m))){
return cljs.core.println.call(null,[cljs.core.str("\n  Please see http://clojure.org/"),cljs.core.str(new cljs.core.Keyword(null,"url","url",276297046).cljs$core$IFn$_invoke$arity$1(m))].join(''));
} else {
return null;
}
} else {
return cljs.core.println.call(null,[cljs.core.str("\n  Please see http://clojure.org/special_forms#"),cljs.core.str(new cljs.core.Keyword(null,"name","name",1843675177).cljs$core$IFn$_invoke$arity$1(m))].join(''));
}
} else {
if(cljs.core.truth_(new cljs.core.Keyword(null,"macro","macro",-867863404).cljs$core$IFn$_invoke$arity$1(m))){
cljs.core.println.call(null,"Macro");
} else {
}

if(cljs.core.truth_(new cljs.core.Keyword(null,"repl-special-function","repl-special-function",1262603725).cljs$core$IFn$_invoke$arity$1(m))){
cljs.core.println.call(null,"REPL Special Function");
} else {
}

cljs.core.println.call(null," ",new cljs.core.Keyword(null,"doc","doc",1913296891).cljs$core$IFn$_invoke$arity$1(m));

if(cljs.core.truth_(new cljs.core.Keyword(null,"protocol","protocol",652470118).cljs$core$IFn$_invoke$arity$1(m))){
var seq__4561 = cljs.core.seq.call(null,new cljs.core.Keyword(null,"methods","methods",453930866).cljs$core$IFn$_invoke$arity$1(m));
var chunk__4562 = null;
var count__4563 = (0);
var i__4564 = (0);
while(true){
if((i__4564 < count__4563)){
var vec__4565 = cljs.core._nth.call(null,chunk__4562,i__4564);
var name = cljs.core.nth.call(null,vec__4565,(0),null);
var map__4566 = cljs.core.nth.call(null,vec__4565,(1),null);
var map__4566__$1 = ((((!((map__4566 == null)))?((((map__4566.cljs$lang$protocol_mask$partition0$ & (64))) || (map__4566.cljs$core$ISeq$))?true:false):false))?cljs.core.apply.call(null,cljs.core.hash_map,map__4566):map__4566);
var doc = cljs.core.get.call(null,map__4566__$1,new cljs.core.Keyword(null,"doc","doc",1913296891));
var arglists = cljs.core.get.call(null,map__4566__$1,new cljs.core.Keyword(null,"arglists","arglists",1661989754));
cljs.core.println.call(null);

cljs.core.println.call(null," ",name);

cljs.core.println.call(null," ",arglists);

if(cljs.core.truth_(doc)){
cljs.core.println.call(null," ",doc);
} else {
}

var G__4593 = seq__4561;
var G__4594 = chunk__4562;
var G__4595 = count__4563;
var G__4596 = (i__4564 + (1));
seq__4561 = G__4593;
chunk__4562 = G__4594;
count__4563 = G__4595;
i__4564 = G__4596;
continue;
} else {
var temp__4425__auto__ = cljs.core.seq.call(null,seq__4561);
if(temp__4425__auto__){
var seq__4561__$1 = temp__4425__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,seq__4561__$1)){
var c__3147__auto__ = cljs.core.chunk_first.call(null,seq__4561__$1);
var G__4597 = cljs.core.chunk_rest.call(null,seq__4561__$1);
var G__4598 = c__3147__auto__;
var G__4599 = cljs.core.count.call(null,c__3147__auto__);
var G__4600 = (0);
seq__4561 = G__4597;
chunk__4562 = G__4598;
count__4563 = G__4599;
i__4564 = G__4600;
continue;
} else {
var vec__4568 = cljs.core.first.call(null,seq__4561__$1);
var name = cljs.core.nth.call(null,vec__4568,(0),null);
var map__4569 = cljs.core.nth.call(null,vec__4568,(1),null);
var map__4569__$1 = ((((!((map__4569 == null)))?((((map__4569.cljs$lang$protocol_mask$partition0$ & (64))) || (map__4569.cljs$core$ISeq$))?true:false):false))?cljs.core.apply.call(null,cljs.core.hash_map,map__4569):map__4569);
var doc = cljs.core.get.call(null,map__4569__$1,new cljs.core.Keyword(null,"doc","doc",1913296891));
var arglists = cljs.core.get.call(null,map__4569__$1,new cljs.core.Keyword(null,"arglists","arglists",1661989754));
cljs.core.println.call(null);

cljs.core.println.call(null," ",name);

cljs.core.println.call(null," ",arglists);

if(cljs.core.truth_(doc)){
cljs.core.println.call(null," ",doc);
} else {
}

var G__4601 = cljs.core.next.call(null,seq__4561__$1);
var G__4602 = null;
var G__4603 = (0);
var G__4604 = (0);
seq__4561 = G__4601;
chunk__4562 = G__4602;
count__4563 = G__4603;
i__4564 = G__4604;
continue;
}
} else {
return null;
}
}
break;
}
} else {
return null;
}
}
});
