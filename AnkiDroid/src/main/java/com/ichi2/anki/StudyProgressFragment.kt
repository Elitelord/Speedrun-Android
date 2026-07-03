/*
 * Speedrun: the MCAT "Progress" page — the three honest scores (Memory /
 * Performance / Readiness) with expandable per-section detail, computed on the
 * shared Rust backend (same RPCs as desktop). Detailed graphs live on their own
 * page (AnkiDroid Statistics), reachable from the button here.
 *
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */
package com.ichi2.anki

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.fragment.app.Fragment
import anki.scheduler.MemoryScore
import anki.scheduler.MemoryScoreResponse
import anki.scheduler.ReadinessScoreResponse
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.pages.Statistics
import kotlin.math.roundToInt

class StudyProgressFragment : Fragment() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        webView = WebView(requireContext())
        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(Bridge(), "SR")
        return webView
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        launchCatchingTask {
            val html =
                withCol {
                    val tags =
                        backend.getInterleaveConfig().topicTagsList.ifEmpty { DEFAULT_TOPICS }
                    buildHtml(
                        memory =
                            backend.computeMemoryScore(
                                search = "",
                                topicTags = tags,
                                topicMinReviews = 0,
                                deckMinReviews = 0,
                            ),
                        performance =
                            backend.computePerformanceScore(
                                search = "",
                                topicTags = tags,
                                topicMinReviews = 0,
                                deckMinReviews = 0,
                            ),
                        readiness =
                            backend.computeReadinessScore(
                                search = "",
                                topicTags = tags,
                                topicMinReviews = 0,
                                deckMinReviews = 0,
                            ),
                    )
                }
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun openGraphs() {
            requireActivity().runOnUiThread {
                startActivity(
                    SingleFragmentActivity.getIntent(requireContext(), Statistics::class),
                )
            }
        }
    }

    companion object {
        private val DEFAULT_TOPICS =
            listOf("mcat::biobiochem", "mcat::chemphys", "mcat::psychsoc")

        private fun pct(v: Float): Int = (v.coerceIn(0f, 1f) * 100).roundToInt()

        private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        private fun section(label: String): String = esc(label.substringAfterLast("::"))

        private fun memoryRows(resp: MemoryScoreResponse): String {
            val sb = StringBuilder()
            for (t in resp.topicsList) {
                val v =
                    if (t.shown && t.cardsWithState > 0) {
                        "${pct(t.estimate)}%"
                    } else {
                        "<span class='muted'>not enough data</span>"
                    }
                sb.append("<div class='row'><span>${section(t.label)}</span><span>$v</span></div>")
            }
            return sb.toString()
        }

        private fun readinessRows(resp: ReadinessScoreResponse): String {
            val sb = StringBuilder()
            for (t in resp.topicsList) {
                val m: MemoryScore = t.mastery
                val v =
                    if (m.shown && m.cardsWithState > 0) {
                        "${t.scaledEstimate.roundToInt()} " +
                            "<span class='muted'>(${t.scaledLow.roundToInt()}" +
                            "–${t.scaledHigh.roundToInt()})</span>"
                    } else {
                        "<span class='muted'>not enough data</span>"
                    }
                sb.append("<div class='row'><span>${section(m.label)}</span><span>$v</span></div>")
            }
            sb.append(
                "<div class='row'><span>CARS</span>" +
                    "<span class='muted'>coming with the CARS module</span></div>",
            )
            return sb.toString()
        }

        private fun scoreCard(
            label: String,
            resp: MemoryScoreResponse,
            sub: String,
        ): String {
            val o = resp.overall
            val big =
                if (o.shown && o.cardsWithState > 0) {
                    "<div class='big'>${pct(o.estimate)}<span class='unit'>%</span></div>"
                } else {
                    "<div class='big muted'>—</div>"
                }
            return "<details class='card'><summary><div class='label'>$label</div>$big" +
                "<div class='sub'>$sub</div></summary><div class='rows'>" +
                memoryRows(resp) + "</div></details>"
        }

        private fun readinessCard(resp: ReadinessScoreResponse): String {
            val big: String
            val sub: String
            if (resp.shown) {
                val covered = (resp.coverage * 4).roundToInt()
                big =
                    "<div class='big'>${resp.scaledEstimate.roundToInt()}" +
                    "<span class='unit'>/528</span></div>"
                sub = "$covered/4 sections studied"
            } else {
                big = "<div class='big muted'>—</div>"
                sub = "study more to unlock"
            }
            return "<details class='card' open><summary><div class='label'>Readiness</div>$big" +
                "<div class='sub'>$sub</div></summary><div class='rows'>" +
                readinessRows(resp) + "</div></details>"
        }

        fun buildHtml(
            memory: MemoryScoreResponse,
            performance: MemoryScoreResponse,
            readiness: ReadinessScoreResponse,
        ): String {
            val style =
                """
                <style>
                :root{--blue:#2563eb;--soft:#eff4ff;--ink:#1e293b;--muted:#64748b;--border:#e5e9f0;color-scheme:light;}
                html,body{background:#fff!important;color:var(--ink)!important;margin:0;
                    font-family:sans-serif;font-size:15px;}
                #app{padding:1.2em 1.1em 3em;}
                h1{font-size:1.5em;margin:0 0 .1em;}
                .lead{color:var(--muted);margin:0 0 1.2em;font-size:.92em;}
                .card{border:1px solid var(--border);border-radius:14px;padding:1em 1.1em;
                    margin-bottom:.9em;background:#fff;box-shadow:0 1px 3px rgba(15,23,42,.05);}
                summary{list-style:none;cursor:pointer;}
                summary::-webkit-details-marker{display:none;}
                .label{color:var(--muted);font-size:.8em;text-transform:uppercase;
                    letter-spacing:.04em;font-weight:600;}
                .big{font-size:2.4em;font-weight:700;line-height:1.15;}
                .big .unit{font-size:.35em;color:var(--muted);font-weight:500;}
                .big.muted{color:var(--muted);font-weight:500;font-size:2em;}
                .sub{color:var(--muted);font-size:.85em;}
                .rows{margin-top:.7em;border-top:1px solid var(--border);padding-top:.4em;}
                .row{display:flex;justify-content:space-between;padding:.4em 0;
                    border-bottom:1px solid var(--border);}
                .row:last-child{border-bottom:none;}
                .row span:first-child{font-weight:600;text-transform:capitalize;}
                .muted{color:var(--muted);font-weight:normal;}
                .hint{color:var(--muted);font-size:.8em;margin:-.4em 0 1em;}
                .btn{display:inline-block;margin-top:.6em;padding:.6em 1.2em;border:none;
                    border-radius:8px;background:var(--blue);color:#fff;font-size:1em;}
                </style>
                """.trimIndent()
            val body =
                "<div id='app'><h1>Progress</h1>" +
                    "<p class='lead'>Your three honest scores on the 472–528 MCAT scale. " +
                    "Tap a card to see it by section.</p>" +
                    scoreCard("Memory", memory, "recall right now") +
                    scoreCard("Performance", performance, "exam-style accuracy") +
                    readinessCard(readiness) +
                    "<p class='hint'>Readiness is the sum of the four sections; unstudied " +
                    "sections widen the range.</p>" +
                    "<button class='btn' onclick='SR.openGraphs()'>View detailed graphs</button>" +
                    "</div>"
            return "<!doctype html><html><head><meta name='viewport' " +
                "content='width=device-width, initial-scale=1'>$style</head><body>$body</body></html>"
        }
    }
}
