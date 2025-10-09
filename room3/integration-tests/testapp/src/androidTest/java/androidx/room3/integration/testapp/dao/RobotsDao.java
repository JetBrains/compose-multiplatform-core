/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.room3.integration.testapp.dao;

import androidx.room3.Dao;
import androidx.room3.Insert;
import androidx.room3.Query;
import androidx.room3.RoomWarnings;
import androidx.room3.integration.testapp.vo.Cluster;
import androidx.room3.integration.testapp.vo.Hivemind;
import androidx.room3.integration.testapp.vo.Robot;
import androidx.room3.integration.testapp.vo.RobotAndHivemind;

import java.util.List;
import java.util.UUID;

@Dao
@SuppressWarnings(RoomWarnings.RELATION_QUERY_WITHOUT_TRANSACTION)
public interface RobotsDao {

    @Insert
    void putHivemind(Hivemind hivemind);

    @Insert
    void putRobot(Robot robot);

    @Query("SELECT * FROM Hivemind")
    List<Cluster> getCluster();

    @Query("SELECT * FROM Robot WHERE mHiveId = :hiveId")
    List<Robot> getHiveRobots(UUID hiveId);

    @Query("SELECT * FROM Robot")
    List<RobotAndHivemind> getRobotsWithHivemind();
}
