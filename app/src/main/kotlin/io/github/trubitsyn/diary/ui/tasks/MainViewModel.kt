/*
 * Copyright 2016 Nikola Trubitsyn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.trubitsyn.diary.ui.tasks

import android.databinding.ObservableInt
import android.view.View
import io.github.trubitsyn.diary.api.DataSource
import io.github.trubitsyn.diary.api.task.Task
import io.github.trubitsyn.diary.api.task.TasksMerger
import io.github.trubitsyn.diary.formatter.SimpleTaskFormatter
import io.github.trubitsyn.diary.formatter.TaskFormatter
import io.github.trubitsyn.diary.ui.ViewModel
import org.joda.time.LocalDate
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.lang.kotlin.FunctionSubscriber
import rx.schedulers.Schedulers
import java.util.*

class MainViewModel(private val dataSource: DataSource, private val listener: DataListener) : ViewModel {
    val progressVisibility = ObservableInt(View.VISIBLE)
    val dataVisibility = ObservableInt(View.GONE)
    val errorVisibility = ObservableInt(View.GONE)
    private val formatter = SimpleTaskFormatter()

    fun fetch(nextStudyDayDate: LocalDate) {
        listener.onBeginFetching(nextStudyDayDate)
        showProgress()

        fetchData(nextStudyDayDate).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(FunctionSubscriber<List<Task>>()
                .onNext {
                    val formatted = appendEntries(it, formatter)
                    listener.onDataFetched(formatted)
                }
                .onCompleted {
                    showData()
                }
                .onError {
                    showError()
                }
        )
    }

    fun fetch(dates: List<LocalDate>) {
        listener.onBeginFetchingRange(dates[0], dates[dates.size - 1])
        showProgress()

        val listOfLists = ArrayList<List<Task>>()

        fetchData(dates).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(FunctionSubscriber<List<Task>>()
                .onNext {
                    listOfLists.add(it)
                }
                .onCompleted {
                    showData()

                    val mergedTasks = TasksMerger(listOfLists.flatten()).merge()
                    val formatted = appendEntries(mergedTasks, formatter)
                    listener.onDataFetched(formatted)
                }
                .onError {
                    showError()
                }
        )
    }

    private fun showProgress() {
        progressVisibility.set(View.VISIBLE)
        dataVisibility.set(View.GONE)
        errorVisibility.set(View.GONE)
    }

    private fun showData() {
        progressVisibility.set(View.GONE)
        errorVisibility.set(View.GONE)
        dataVisibility.set(View.VISIBLE)
    }

    private fun showError() {
        progressVisibility.set(View.GONE)
        dataVisibility.set(View.GONE)
        errorVisibility.set(View.VISIBLE)
    }

    private fun appendEntries(tasks: List<Task>, formatter: TaskFormatter): String {
        val builder = StringBuilder()
        for (task in tasks) {
            builder.append(formatter.format(task))
        }
        return builder.toString()
    }

    private fun fetchData(dates: List<LocalDate>): Observable<List<Task>> {
        return Observable.create { subscriber ->
            for (date in dates) {
                subscriber.onNext(dataSource.getTasks(date))
            }
            subscriber.onCompleted()
        }
    }

    private fun fetchData(date: LocalDate): Observable<List<Task>> {
        return Observable.create { subscriber ->
            subscriber.onNext(dataSource.getTasks(date))
            subscriber.onCompleted()
        }
    }

    override fun destroy() {

    }

    interface DataListener {
        fun onBeginFetching(date: LocalDate)
        fun onBeginFetchingRange(from: LocalDate, to: LocalDate)
        fun onDataFetched(data: String)
    }
}
