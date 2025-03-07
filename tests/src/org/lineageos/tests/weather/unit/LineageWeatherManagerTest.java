/**
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.tests.weather.unit;

import android.location.Location;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import lineageos.weather.LineageWeatherManager;
import lineageos.weather.LineageWeatherManager.LookupCityRequestListener;
import lineageos.weather.LineageWeatherManager.WeatherServiceProviderChangeListener;
import lineageos.weather.LineageWeatherManager.WeatherUpdateRequestListener;
import lineageos.weather.ILineageWeatherManager;
import lineageos.weather.RequestInfo;
import lineageos.weather.WeatherInfo;
import lineageos.weather.WeatherLocation;
import org.lineageos.tests.common.MockIBinderStubForInterface;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class LineageWeatherManagerTest extends AndroidTestCase {

    private LineageWeatherManager mWeatherManager;
    private static final String CITY_NAME = "Seattle, WA";
    private static final int COUNTDOWN = 1;
    private static final int REQUEST_ID = 42;
    private static final String MOCKED_WEATHER_PROVIDER_LABEL = "Mock'd Weather Service";
    private ILineageWeatherManager.Stub mILineageWeatherManagerSpy;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        setUpMockILineageWeatherManager();
    }

    private void setUpMockILineageWeatherManager() {
        mWeatherManager = LineageWeatherManager.getInstance(getContext());
        Field f;
        try {
            f = mWeatherManager.getClass().getDeclaredField("sWeatherManagerService");
            f.setAccessible(true);

            mILineageWeatherManagerSpy
                    = MockIBinderStubForInterface.getMockInterface(ILineageWeatherManager.Stub.class);
            f.set(mWeatherManager, mILineageWeatherManagerSpy);

            Mockito.doAnswer(new WeatherUpdateRequestAnswer())
                    .when(mILineageWeatherManagerSpy).updateWeather(Mockito.any(RequestInfo.class));

            Mockito.doAnswer(new LookUpCityAnswer())
                    .when(mILineageWeatherManagerSpy).lookupCity(Mockito.any(RequestInfo.class));

            Mockito.doAnswer(new GetActiveWeatherServiceProviderLabelAnser())
                    .when(mILineageWeatherManagerSpy).getActiveWeatherServiceProviderLabel();

            Mockito.doAnswer(new CancelRequestAnswer())
                    .when(mILineageWeatherManagerSpy).cancelRequest(Mockito.eq(REQUEST_ID));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static class WeatherUpdateRequestAnswer implements Answer<Integer> {

        @Override
        public Integer answer(InvocationOnMock invocation) throws Throwable {
            final RequestInfo requestInfo = (RequestInfo) invocation.getArguments()[0];
            if (requestInfo.getRequestType() == RequestInfo.TYPE_WEATHER_BY_GEO_LOCATION_REQ) {
                assertNotNull(requestInfo.getLocation());
            } else {
                assertNotNull(requestInfo.getWeatherLocation());
            }
            final WeatherInfo weatherInfo = new WeatherInfo.Builder(CITY_NAME,
                    30d, requestInfo.getTemperatureUnit()).build();
            requestInfo.getRequestListener().onWeatherRequestCompleted(requestInfo,
                    LineageWeatherManager.RequestStatus.COMPLETED, weatherInfo);
            return REQUEST_ID;
        }
    }

    private static class LookUpCityAnswer implements Answer<Integer> {

        @Override
        public Integer answer(InvocationOnMock invocation) throws Throwable {
            final RequestInfo requestInfo = (RequestInfo) invocation.getArguments()[0];
            final List<WeatherLocation> locations = new ArrayList<>();
            final String cityName = requestInfo.getCityName();
            assertNotNull(cityName);
            locations.add(new WeatherLocation.Builder(cityName).build());
            requestInfo.getRequestListener().onLookupCityRequestCompleted(requestInfo,
                    LineageWeatherManager.RequestStatus.COMPLETED, locations);
            return REQUEST_ID;
        }
    }

    private static class GetActiveWeatherServiceProviderLabelAnser implements Answer<String> {

        @Override
        public String answer(InvocationOnMock invocation) throws Throwable {
            return MOCKED_WEATHER_PROVIDER_LABEL;
        }
    }

    private static class CancelRequestAnswer implements Answer<Void> {

        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
            final int requestId = (Integer) invocation.getArguments()[0];
            assertEquals(requestId, REQUEST_ID);
            return null;
        }
    }

    @SmallTest
    public void testGetActiveWeatherServiceProviderLabel() {
        String providerLabel = mWeatherManager.getActiveWeatherServiceProviderLabel();
        assertEquals(MOCKED_WEATHER_PROVIDER_LABEL, providerLabel);
    }

    @MediumTest
    public void testLookupCity() {
        final CountDownLatch signal = new CountDownLatch(COUNTDOWN);
        final boolean[] error = {false};
        mWeatherManager.lookupCity(CITY_NAME,
                new LookupCityRequestListener() {
                    @Override
                    public void onLookupCityRequestCompleted(int status,
                            List<WeatherLocation> locations) {
                        final int totalLocations = locations != null ? locations.size() : 0;
                        if (status != LineageWeatherManager.RequestStatus.COMPLETED
                                || totalLocations < 1) {
                            error[0] = true;
                        }
                        signal.countDown();
                    }
                });
        try {
            signal.await();
            assertFalse(error[0]);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    @SmallTest
    public void testRegisterListener() {
        mWeatherManager.registerWeatherServiceProviderChangeListener(mListener);

        try {
            mWeatherManager.registerWeatherServiceProviderChangeListener(mListener);
            throw new AssertionError("Listener was registered twice!");
        } catch (IllegalArgumentException e) {
            //EXPECTED
        }

        mWeatherManager.unregisterWeatherServiceProviderChangeListener(mListener);

        try {
            mWeatherManager.unregisterWeatherServiceProviderChangeListener(mListener);
            throw new AssertionError("Listener was de-registered twice!");
        } catch (IllegalArgumentException e) {
            //EXPECTED
        }
    }

    private WeatherServiceProviderChangeListener mListener
            = new WeatherServiceProviderChangeListener() {
        @Override
        public void onWeatherServiceProviderChanged(String providerLabel) {}
    };

    @MediumTest
    public void testRequestWeatherUpdateByWeatherLocation() {
        final CountDownLatch signal = new CountDownLatch(COUNTDOWN);
        final WeatherLocation weatherLocation = new WeatherLocation.Builder(CITY_NAME).build();
        final boolean[] error = {false};
        mWeatherManager.requestWeatherUpdate(weatherLocation, new WeatherUpdateRequestListener() {
            @Override
            public void onWeatherRequestCompleted(int status, WeatherInfo weatherInfo) {
                if (status != LineageWeatherManager.RequestStatus.COMPLETED
                        || !weatherInfo.getCity().equals(CITY_NAME)) {
                    error[0] = true;
                }
                signal.countDown();
            }
        });
        try {
            signal.await();
            assertFalse(error[0]);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    @MediumTest
    public void testRequestWeatherUpdateByLocation() {
        final CountDownLatch signal = new CountDownLatch(COUNTDOWN);
        final Location location = new Location("test_location_provider");
        final boolean[] error = {false};
        mWeatherManager.requestWeatherUpdate(location, new WeatherUpdateRequestListener() {
            @Override
            public void onWeatherRequestCompleted(int status, WeatherInfo weatherInfo) {
                if (status != LineageWeatherManager.RequestStatus.COMPLETED
                        || !weatherInfo.getCity().equals(CITY_NAME)) {
                    error[0] = true;
                }
                signal.countDown();
            }
        });
        try {
            signal.await();
            assertFalse(error[0]);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    @SmallTest
    public void testCancelRequest() {
        mWeatherManager.cancelRequest(REQUEST_ID);
    }
}
