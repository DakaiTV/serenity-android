/**
 * The MIT License (MIT)
 * Copyright (c) 2012 David Carver
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS
 * OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF
 * OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package us.nineworlds.serenity.core.services.test;

import android.content.Intent;
import us.nineworlds.plex.rest.PlexappFactory;
import us.nineworlds.serenity.MockSerenityApplication;
import us.nineworlds.serenity.core.services.MainMenuIntentService;

/**
 * @author dcarver
 *
 */
public class MockMainMenuIntentService extends MainMenuIntentService {

	@Override
	protected PlexappFactory getPlexFactoryInstance() {
		MockSerenityApplication app = new MockSerenityApplication();
		app.onCreate();
		return MockSerenityApplication.getPlexFactory();
	}
	
	@Override
	public void sendMessageResults(Intent intent) {
		
	}
	
	@Override
	public void onHandleIntent(Intent intent) {
		super.onHandleIntent(intent);
	}
	
	
}
