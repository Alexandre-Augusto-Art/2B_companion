package br.offgr1d.companion.alerts

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import br.offgr1d.companion.App
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Procura novidades de hora em hora, com o app fechado. Continua depois de reiniciar o celular. */
class AlertJob : JobService() {
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val app = application as App
        job = app.scope.launch {
            val retry = try {
                app.checkAlerts() == null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                true
            }
            jobFinished(params, retry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return true
    }

    companion object {
        private const val ID = 10
        private const val HOUR = 60 * 60_000L

        fun schedule(c: Context, on: Boolean) {
            val js = c.getSystemService(JobScheduler::class.java)
            if (!on) return js.cancel(ID)
            if (js.getPendingJob(ID) != null) return // reagendar zeraria a contagem
            js.schedule(
                JobInfo.Builder(ID, ComponentName(c, AlertJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(HOUR)
                    .setPersisted(true)
                    .build(),
            )
        }
    }
}
